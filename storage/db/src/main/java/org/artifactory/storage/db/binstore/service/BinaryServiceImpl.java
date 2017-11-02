/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2016 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.storage.db.binstore.service;

import com.google.common.collect.Lists;
import com.google.common.collect.MapMaker;
import com.google.common.collect.Sets;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.ClusterOperationsService;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.addon.ha.interceptor.ClusterTopologyListener;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.storage.BinariesInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.ha.HaNodeProperties;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.environment.BinaryStoreProperties;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.BinaryInsertRetryException;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.binstore.service.*;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.binstore.dao.BinariesDao;
import org.artifactory.storage.db.binstore.entity.BinaryEntity;
import org.artifactory.storage.db.binstore.exceptions.PruneException;
import org.artifactory.storage.db.binstore.util.BinaryServiceUtils;
import org.artifactory.storage.db.binstore.util.FilestorePruner;
import org.artifactory.storage.db.binstore.visitors.BinaryTreeElementScanner;
import org.artifactory.storage.db.binstore.visitors.EssentialBinaryTreeElementHandler;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.fs.service.ArchiveEntriesService;
import org.artifactory.storage.model.FileBinaryProviderInfo;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.access.client.AccessClient;
import org.jfrog.client.util.Pair;
import org.jfrog.storage.binstore.common.BinaryElementRequestImpl;
import org.jfrog.storage.binstore.common.BinaryStoreContextImpl;
import org.jfrog.storage.binstore.common.ReaderTrackingInputStream;
import org.jfrog.storage.binstore.exceptions.BinaryStorageException;
import org.jfrog.storage.binstore.ifc.*;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;
import org.jfrog.storage.binstore.ifc.model.BinaryProvidersInfo;
import org.jfrog.storage.binstore.ifc.model.BinaryTreeElement;
import org.jfrog.storage.binstore.ifc.model.StorageInfo;
import org.jfrog.storage.binstore.ifc.provider.BinaryProvider;
import org.jfrog.storage.binstore.manager.BinaryProviderManagerImpl;
import org.jfrog.storage.binstore.providers.base.BinaryProviderBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static org.artifactory.storage.db.binstore.util.BinaryServiceUtils.*;

/**
 * The main binary store of Artifactory that delegates to the BinaryProvider chain.
 *
 * @author Yossi Shaul
 */
@Service
@Reloadable(beanClass = BinaryService.class, initAfter = {AccessService.class, ClusterOperationsService.class})
public class BinaryServiceImpl implements InternalBinaryService, ClusterTopologyListener {
    private static final Logger log = LoggerFactory.getLogger(BinaryServiceImpl.class);

    @Autowired
    private BinariesDao binariesDao;

    @Autowired
    private ArchiveEntriesService archiveEntriesService;

    @Autowired
    private DbService dbService;

    @Autowired
    private AccessService accessService;

    /**
     * Map of delete protected sha1 checksums to the number of protections (active readers + writer count for each
     * binary)
     */
    private ConcurrentMap<String, Pair<AtomicInteger, Long>> deleteProtectedBinaries;
    private List<GarbageCollectorListener> garbageCollectorListeners;
    private BinaryProviderManager binaryProviderManager;
    private BinaryProviderConfig defaultValues;
    private Lock lock = new ReentrantLock();
    private BinaryProvider binaryProvider;
    private boolean forceBinaryProviderOptimizationOnce = false;

    @PostConstruct
    public void initialize() {
        garbageCollectorListeners = new CopyOnWriteArrayList<>();
        log.debug("Initializing the ConfigurableBinaryProviderManager");
        deleteProtectedBinaries = new MapMaker().makeMap();
        // Generate Default values
        ArtifactoryHome artifactoryHome = ArtifactoryHome.get();
        BinaryStoreProperties storeProperties = new BinaryStoreProperties(artifactoryHome.getDataDir().getPath(),
                artifactoryHome.getSecurityDir().getPath());
        HaNodeProperties haNodeProperties =  artifactoryHome.getHaNodeProperties();
        if (haNodeProperties != null) {
            storeProperties.setClusterDataDir(haNodeProperties.getClusterDataDir());
        }
        defaultValues = storeProperties.toDefaultValues();
        if (ContextHelper.get() != null && haNodeProperties != null) {
            // TODO: [by fsi] this ha enabled needs to be done at the binary store project level
            if (artifactoryHome.isHaConfigured()) {
                defaultValues.setBinaryStoreContext(new BinaryStoreContextImpl(
                        new ArtifactoryLockingMapFactoryProvide(),
                        haNodeProperties.getProperties(), this::exists, this::getAccessClient));
                defaultValues.addParam("serviceId", haNodeProperties.getServerId() + "-binary-store");
            }
        }
        // Set the binarystore.xml file location
        File haAwareEtcDir = artifactoryHome.getEtcDir();
        File userConfigFile = new File(haAwareEtcDir, "binarystore.xml");
        defaultValues.setBinaryStoreXmlPath(userConfigFile.getPath());
        // Finally create an instance of the binary provider manager
        binaryProviderManager = new BinaryProviderManagerImpl(defaultValues);
        // Get the root binary provide from the binary provider manager
        binaryProvider = binaryProviderManager.getFirstBinaryProvider();
    }

    @Override
    public BinaryProviderManager getBinaryProviderManager() {
        return binaryProviderManager;
    }

    @Override
    public void init() {

    }

    @Override
    public void reload(CentralConfigDescriptor oldDescriptor) {

    }

    @PreDestroy
    public void destroy() {
        notifyGCListenersOnDestroy();
        binaryProviderManager.contextDestroyed();
    }

    @Override
    public void addGCListener(GarbageCollectorListener garbageCollectorListener) {
        garbageCollectorListeners.add(garbageCollectorListener);
    }

    @Override
    public void addExternalFileStore(File externalFileDir, ProviderConnectMode connectMode) {
        // The external binary provider works only if the file binary provider is not null
        if (getBinariesDir() == null) {
            return;
        }
        // Prepare parameters for the new External binary provider
        String mode = connectMode.propName;
        String externalDir = externalFileDir.getAbsolutePath();
        String fileStoreDir = defaultValues.getParam("fileStoreDir");
        File fileStoreFullPath = new File(new File(defaultValues.getParam("baseDataDir")), fileStoreDir);
        // create and initialize the external binary providers.
        binaryProviderManager.initializeExternalBinaryProvider(mode, externalDir, fileStoreFullPath.getAbsolutePath(),
                defaultValues);
    }

    @Override
    public void disconnectExternalFilestore(File externalDir, ProviderConnectMode disconnectMode, BasicStatusHolder statusHolder) {
        ExternalBinaryProviderHelper
                .disconnectFromFileStore(this, externalDir, disconnectMode, statusHolder, binaryProviderManager,
                        binariesDao, defaultValues);
    }

    @Override
    public File getBinariesDir() {
        // Get binary providers info tree from the manager
        BinaryProvidersInfo binaryProvidersInfo = binaryProviderManager.getBinaryProvidersInfo();
        BinaryTreeElement<BinaryProviderInfo> treeElement = binaryProvidersInfo.rootTreeElement;
        // Collect all the file binary providers in list
        List<FileBinaryProviderInfo> providersInfos = Lists.newArrayList();
        collectFileBinaryProvidersDirsInternal(providersInfos, treeElement);
        // Get the First binary provider
        FileBinaryProviderInfo fileBinaryProviderInfo = providersInfos.size() > 0 ? providersInfos.get(0) : null;
        if (fileBinaryProviderInfo != null) {
            // We need the wrapper to avoid binary dir recalculation even if there is no file binary provider
            return fileBinaryProviderInfo.getFileStoreDir();
        }
        return null;
    }

    @Override
    public StorageInfo getStorageInfoSummary() {
        // Get binary providers info tree from the manager
        BinaryProvidersInfo binaryProvidersInfo = binaryProviderManager.getBinaryProvidersInfo();
        BinaryTreeElement<BinaryProviderInfo> treeElement = binaryProvidersInfo.rootTreeElement;
        // Collect all the  binary providers in list
        List<BinaryTreeElement<BinaryProviderInfo>> providersInfos = Lists.newArrayList();
        collectBinaryProviderInfo(providersInfos, treeElement);
        // Quota is for the final NFS if exists, but if final is infinite quota should be for cache fs binary provider
        // Take the smallest space available from all the providers that are not cache fs
        StorageInfo cacheFsStorageInfo = null;
        StorageInfo smallestStorageInfo = null;
        for (BinaryTreeElement<BinaryProviderInfo> providerInfo : providersInfos) {
            if (providerInfo != null) {
                BinaryProviderInfo data = providerInfo.getData();
                StorageInfo storageInfo = data.getStorageInfo();
                String type = data.getProperties().get("type");
                if ("cache-fs".equals(type)) {
                    cacheFsStorageInfo = storageInfo;
                    // An EFS mount generates negative free space!
                    if (cacheFsStorageInfo.getFreeSpace() < 0L || cacheFsStorageInfo.getFreeSpaceInPercent() < 0L ||
                            cacheFsStorageInfo.getFreeSpaceInPercent() > 100L) {
                        // It's infinite
                        cacheFsStorageInfo = BinaryProviderBase.INFINITE_STORAGE_INFO;
                    }
                } else if (!BinaryProviderBase.EMPTY_STORAGE_INFO.equals(storageInfo)) {
                    if (smallestStorageInfo == null) {
                        smallestStorageInfo = storageInfo;
                    } else {
                        // An EFS mount generates negative free space!
                        if (storageInfo.getFreeSpaceInPercent() > 0L &&
                                smallestStorageInfo.getFreeSpaceInPercent() > storageInfo.getFreeSpaceInPercent()) {
                            // TODO: [by fsi] May be have the caller aware of many layers?
                            smallestStorageInfo = storageInfo;
                        }
                    }
                }
            }
        }
        if (smallestStorageInfo != null) {
            if (BinaryProviderBase.INFINITE_STORAGE_INFO.equals(smallestStorageInfo) ||
                    smallestStorageInfo.getFreeSpace() == Long.MAX_VALUE || smallestStorageInfo.getFreeSpace() == -1L) {
                // If there is a chae fs above the infinite storage use this to check quota
                if (cacheFsStorageInfo != null) {
                    return cacheFsStorageInfo;
                }
            }
            return smallestStorageInfo;
        }
        // No smallest found, if cache use it.
        if (cacheFsStorageInfo != null) {
            return cacheFsStorageInfo;
        }
        // No storage info relevant found, return unknown
        return BinaryProviderBase.UNKNOWN_STORAGE_INFO;
    }

    @Override
    @Nullable
    public BinaryInfo addBinaryRecord(String sha1, String md5, long length) {
        try {
            BinaryEntity result = binariesDao.load(sha1);
            if (result == null) {
                // It does not exists in the DB
                // Let's check if in bin provider
                BinaryElementRequestImpl request = new BinaryElementRequestImpl(sha1, md5, length);
                if (binaryProvider.exists(request).exists()) {
                    // Good let's use it
                    return getTransactionalMe().insertRecordInDb(sha1, md5, length);
                }
                return null;
            }
            return convertToBinaryInfo(result);
        } catch (SQLException e) {
            throw new StorageException("Could not reserved entry '" + sha1 + "'", e);
        }
    }

    @Override
    @Nonnull
    public BinaryInfo addBinary(InputStream in) throws IOException {
        if (in instanceof BinaryServiceInputStream) {
            throw new IllegalStateException("Cannot add binary from checksum deploy " + ((BinaryServiceInputStream) in).getBinaryInfo());
        }

        BinaryInfo binaryInfo;
        BinaryElement bi = binaryProvider.addStream(binaryProviderManager.createBinaryStream(in));
        log.trace("Inserted binary {} to file store", bi.getSha1());
        // From here we managed to create a binary record on the binary provider
        // So, failing on the insert in DB (because saving the file took to long)
        // can be re-tried based on the sha1
        try {
            binaryInfo = getTransactionalMe().insertRecordInDb(bi.getSha1(), bi.getMd5(), bi.getLength());
        } catch (BinaryInsertRetryException e) {
            if (log.isDebugEnabled()) {
                log.info("Retrying add binary after receiving exception", e);
            } else {
                log.info("Retrying add binary after receiving exception: " + e.getMessage());
            }
            binaryInfo = addBinaryRecord(bi.getSha1(), bi.getMd5(), bi.getLength());
            if (binaryInfo == null) {
                throw new StorageException("Failed to add binary record with SHA1 " + bi.getSha1() +
                        "during retry", e);
            }
        }
        return binaryInfo;
    }

    @Override
    public BinaryProvidersInfo<Map<String, String>> getBinaryProvidersInfo() {
        // Get binary providers info tree from the binary store manager
        BinaryProvidersInfo binaryProvidersInfo = binaryProviderManager.getBinaryProvidersInfo();
        BinaryTreeElement<BinaryProviderInfo> teeElement = binaryProvidersInfo.rootTreeElement;
        // Create sub tree that contains only essential elements (for the UI)
        BinaryTreeElementScanner<BinaryProviderInfo, Map<String, String>> scanner = new BinaryTreeElementScanner<>();
        EssentialBinaryTreeElementHandler handler = new EssentialBinaryTreeElementHandler();
        BinaryTreeElement<Map<String, String>> scan = scanner.scan(teeElement, handler);
        return new BinaryProvidersInfo<>(binaryProvidersInfo.template, scan);
    }

    @Override
    public InputStream getBinary(String sha1) {
        return new ReaderTrackingInputStream(binaryProvider.getStream(new BinaryElementRequestImpl(sha1)), sha1, this);
    }

    @Override
    public InputStream getBinary(BinaryInfo bi) {
        if (!binaryProvider.exists(new BinaryElementRequestImpl(bi.getSha1(), bi.getMd5(), bi.getLength())).exists()) {
            return null;
        }
        return new BinaryServiceInputStreamWrapper(bi, this);
    }

    @Override
    public BinaryInfo findBinary(String sha1) {
        try {
            BinaryEntity result = binariesDao.load(sha1);
            if (result != null) {
                return convertToBinaryInfo(result);
            }
        } catch (SQLException e) {
            throw new StorageException("Storage error loading checksum '" + sha1 + "'", e);
        }
        return null;
    }

    @Nonnull
    @Override
    public Set<BinaryInfo> findBinaries(@Nullable Collection<String> checksums) {
        Set<BinaryInfo> results = Sets.newHashSet();
        if (checksums == null || checksums.isEmpty()) {
            return results;
        }
        try {
            for (ChecksumType checksumType : ChecksumType.BASE_CHECKSUM_TYPES) {
                Collection<String> validChecksums = extractValid(checksumType, checksums);
                if (!validChecksums.isEmpty()) {
                    Collection<BinaryEntity> found = binariesDao.search(checksumType, validChecksums);
                    results.addAll(found.stream().map(this::convertToBinaryInfo).collect(Collectors.toList()));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Could not search for checksums " + checksums, e);
        }
        return results;
    }

    @Override
    public GarbageCollectorInfo garbageCollect() {
        notifyGCListenersOnStart();
        final GarbageCollectorInfo result = new GarbageCollectorInfo();
        Collection<BinaryEntity> binsToDelete;
        try {
            BinariesInfo countAndSize = binariesDao.getCountAndTotalSize();
            result.initialCount = countAndSize.getBinariesCount();
            result.initialSize = countAndSize.getBinariesSize();
            binsToDelete = binariesDao.findPotentialDeletion();
        } catch (SQLException e) {
            throw new StorageException("Could not find potential Binaries to delete!", e);
        }
        result.stopScanTimestamp = System.currentTimeMillis();
        result.candidatesForDeletion = binsToDelete.size();
        if (result.candidatesForDeletion > 0) {
            log.info("Found {} candidates for deletion", result.candidatesForDeletion);
        }
        // Counts failures, if pass the threshold, gc stops.
        int failures = 0;
        for (BinaryEntity bd : binsToDelete) {
            log.trace("Candidate for deletion: {}", bd);
            try {
                dbService.invokeInTransaction("BinaryCleaner#" + bd.getSha1(), new BinaryCleaner(bd, result));
            } catch (Exception e) {
                failures++;
                String msg = "Caught Exception, trying to clean {} : {}";
                if (failures >= ConstantValues.gcFailCountThreshold.getInt()) {
                    // We're past the allowed fail threshold, fail gc.
                    log.error(msg + ". Aborting Garbage Collection Run.", bd.getSha1(), e.getMessage());
                    log.debug("", e);
                    break;
                } else {
                    log.debug(msg, bd.getSha1(), e.getMessage());
                }
            }
        }

        if (result.checksumsCleaned > 0) {
            result.archivePathsCleaned = getTransactionalMe().deleteUnusedArchivePaths();
            result.archiveNamesCleaned = getTransactionalMe().deleteUnusedArchiveNames();
        }

        result.gcEndTime = System.currentTimeMillis();

        try {
            BinariesInfo countAndSize = binariesDao.getCountAndTotalSize();
            result.printCollectionInfo(countAndSize.getBinariesSize());
        } catch (SQLException e) {
            log.error("Could not list files due to " + e.getMessage());
        }
        boolean success = binaryProviderManager.optimize(forceBinaryProviderOptimizationOnce);
        if (success) {
            forceBinaryProviderOptimizationOnce = false;
        }
        notifyGCListenersOnFinished();
        return result;
    }

    /**
     * Deletes binary row and all dependent rows from the database
     *
     * @param sha1ToDelete Checksum to delete
     * @return True if deleted. False if not found or error
     */
    private boolean deleteEntry(String sha1ToDelete) {
        boolean hadArchiveEntries;
        try {
            hadArchiveEntries = archiveEntriesService.deleteArchiveEntries(sha1ToDelete);
        } catch (Exception e) {
            log.error("Failed to delete archive entries for " + sha1ToDelete, e);
            return false;
        }
        try {
            boolean entryDeleted = binariesDao.deleteEntry(sha1ToDelete) == 1;
            if (!entryDeleted && hadArchiveEntries) {
                log.error("Binary entry " + sha1ToDelete + " had archive entries that are deleted," +
                        " but the binary line was not deleted! Re indexing of archive needed.");
            }
            return entryDeleted;
        } catch (SQLException e) {
            log.error("Could execute delete from binary store of " + sha1ToDelete, e);
        }
        return false;
    }

    @Override
    public int deleteUnusedArchivePaths() {
        try {
            log.debug("Deleting unused archive paths");
            return archiveEntriesService.deleteUnusedPathIds();
        } catch (StorageException e) {
            log.error("Failed to delete unique paths: {}", e.getMessage());
            log.debug("Failed to delete unique paths", e);
            return 0;
        }
    }

    @Override
    public int deleteUnusedArchiveNames() {
        try {
            log.debug("Deleting unused archive names");
            return archiveEntriesService.deleteUnusedNameIds();
        } catch (StorageException e) {
            log.error("Failed to delete unique archive names: {}", e.getMessage());
            log.debug("Failed to delete unique archive paths", e);
            return 0;
        }
    }

    @Override
    public int incrementNoDeleteLock(String sha1) {
        Pair<AtomicInteger, Long> pair = deleteProtectedBinaries
                .putIfAbsent(sha1, new Pair<>(new AtomicInteger(1), System.currentTimeMillis()));
        if (pair == null) {
            return 1;
        } else {
            pair.setSecond(System.currentTimeMillis());
            return pair.getFirst().incrementAndGet();
        }
    }

    @Override
    public void decrementNoDeleteLock(String sha1) {
        AtomicInteger usageCount = deleteProtectedBinaries.get(sha1).getFirst();
        if (usageCount != null) {
            usageCount.decrementAndGet();
        }
    }

    @Override
    public Collection<BinaryInfo> findAllBinaries() {
        try {
            Collection<BinaryEntity> allBinaries = binariesDao.findAll();
            List<BinaryInfo> result = new ArrayList<>(allBinaries.size());
            result.addAll(allBinaries.stream().map(this::convertToBinaryInfo).collect(Collectors.toList()));
            return result;
        } catch (SQLException e) {
            throw new StorageException("Could not retrieve all binary entries", e);
        }
    }

    @Override
    @Nonnull
    public BinaryInfo insertRecordInDb(String sha1, String md5, long length) throws StorageException {
        BinaryEntityWithValidation dataRecord = new BinaryEntityWithValidation(sha1, md5, length);
        if (!dataRecord.isValid()) {
            throw new StorageException("Cannot insert invalid binary record: " + dataRecord);
        }
        try {
            boolean binaryExists = binariesDao.exists(sha1);
            if (!binaryExists) {
                createDataRecord(dataRecord, sha1);
            }
            // Always reselect from DB before returning
            BinaryEntity justInserted = binariesDao.load(sha1);
            if (justInserted == null) {
                throw new StorageException("Could not find just inserted binary record: " + dataRecord);
            }
            return convertToBinaryInfo(justInserted);
        } catch (SQLException e) {
            throw new StorageException("Failed to insert new binary record: " + e.getMessage(), e);
        }
    }

    /**
     * @return Number of binaries and total size stored in the binary store
     */
    @Override
    public BinariesInfo getBinariesInfo() {
        try {
            return binariesDao.getCountAndTotalSize();
        } catch (SQLException e) {
            throw new StorageException("Could not calculate total size due to " + e.getMessage(), e);
        }
    }

    @Override
    public long getStorageSize() {
        return getBinariesInfo().getBinariesSize();
    }

    @Override
    public void ping() {
        // Ping storage
        try {
            binaryProviderManager.ping();
        } catch (BinaryStorageException bse) {
            log.warn("Binary provider failed ping attempt: {}", bse.getMessage());
            log.debug("", bse);
            throw bse;
        }
        // Ping DB
        try {
            if (binariesDao.exists("does not exists")) {
                throw new StorageException("Select entry fails");
            }
        } catch (SQLException e) {
            throw new StorageException("Accessing Binary Store DB failed with " + e.getMessage(), e);
        }
    }

    @Override
    public void prune(BasicStatusHolder statusHolder) {
        boolean locked = lock.tryLock();
        if (locked) {
            try {
                FilestorePruner pruner =
                        new FilestorePruner((BinaryProviderBase) binaryProviderManager.getFirstBinaryProvider(),
                        this::isActivelyUsed, BinaryServiceUtils.binariesDaoSearch(binariesDao), statusHolder);
                pruner.prune();
            } finally {
                lock.unlock();
            }
        } else {
            throw new PruneException("The prune process is already running");
        }
    }

    /**
     * @param sha1 sha1 checksum of the binary to check
     * @return True if the given binary is currently used by a reader (e.g., open stream) or writer
     */
    @Override
    public boolean isActivelyUsed(String sha1) {
        Pair<AtomicInteger, Long> pair = deleteProtectedBinaries.get(sha1);
        return pair != null && pair.getFirst().get() > 0;
    }

    private Collection<String> extractValid(ChecksumType checksumType, Collection<String> checksums) {
        Collection<String> results = Sets.newHashSet();
        results.addAll(checksums.stream().filter(checksumType::isValid).collect(Collectors.toList()));
        return results;
    }

    private InternalBinaryService getTransactionalMe() {
        return ContextHelper.get().beanForType(InternalBinaryService.class);
    }

    private BinaryInfo convertToBinaryInfo(BinaryEntity bd) {
        return new BinaryInfoImpl(bd.getSha1(), bd.getMd5(), bd.getLength());
    }

    private void notifyGCListenersOnStart() {
        garbageCollectorListeners.forEach(GarbageCollectorListener::start);
    }

    private void notifyGCListenersOnFinished() {
        garbageCollectorListeners.forEach(GarbageCollectorListener::finished);
    }

    private void notifyGCListenersOnDestroy() {
        garbageCollectorListeners.forEach(GarbageCollectorListener::destroy);
    }

    @Override
    public List<String> getAndManageErrors() {
        List<String> errors = binaryProviderManager.getErrors();
        if (errors.size() > 0) {
            forceOptimizationOnce();
        }
        return errors;
    }

    @Override
    public void forceOptimizationOnce() {
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        HaCommonAddon haCommonAddon = addonsManager.addonByType(HaCommonAddon.class);
        if (haCommonAddon.isHaEnabled() && !haCommonAddon.isPrimary()) {
            haCommonAddon.forceOptimizationOnce();
        } else {
            forceBinaryProviderOptimizationOnce = true;
        }
    }

    private void createDataRecord(BinaryEntity dataRecord, String sha1) throws SQLException {
        // insert a new binary record to the db
        try {
            binariesDao.create(dataRecord);
        } catch (SQLException e) {
            if (isDuplicatedEntryException(e)) {
                log.debug("Simultaneous insert of binary {} detected, binary will be checked.", sha1, e);
                throw new BinaryInsertRetryException(convertToBinaryInfo(dataRecord), e);
            } else {
                throw e;
            }
        }
    }

    public boolean isFileExist(String sha1) {
        return binaryProvider.exists(new BinaryElementRequestImpl(sha1)).exists();
    }

    @Override
    public void onContextCreated() {
        binaryProviderManager.contextCreated();
    }

    @Override
    public void onContextReady() {
        // nop
    }

    @Override
    public void clusterTopologyChanged(List<ArtifactoryServer> activeNodes) {
        List<ClusterNode> active = activeNodes.stream()
                .filter(Objects::nonNull)
                .map(BinaryServiceUtils::clusterNodeFromArtServer)
                .collect(Collectors.toList());
        binaryProviderManager.clusterChanged(active);
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {

    }

    private boolean exists(String sha1) {
        try {
            return binariesDao.exists(sha1);
        } catch (SQLException sql) {
            log.debug("Failed existence check of path " + sha1, sql);
            throw new BinaryStorageException("Failed existence check of checksum " + sha1 + ": " + sql.getMessage(),
                    sql, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private AccessClient getAccessClient() {
        return accessService.getAccessClient();
    }

    /**
     * Deletes a single binary from the database and filesystem if not in use.
     */
    private class BinaryCleaner implements Callable<Void> {
        private final GarbageCollectorInfo result;
        private final BinaryEntity bd;

        public BinaryCleaner(BinaryEntity bd, GarbageCollectorInfo result) {
            this.result = result;
            this.bd = bd;
        }

        @Override
        public Void call() throws Exception {
            String sha1 = bd.getSha1();
            deleteProtectedBinaries.putIfAbsent(sha1, new Pair<>(new AtomicInteger(0), System.currentTimeMillis()));
            Pair<AtomicInteger, Long> pair = deleteProtectedBinaries.get(sha1);
            if (pair.getFirst().compareAndSet(0, -30)) {
                log.debug("Targeting '{}' for deletion as it not seems to be used", sha1);
                try {
                    if (deleteEntry(sha1)) {
                        log.trace("Deleted {} record from binaries table", sha1);
                        result.checksumsCleaned++;
                        if (binaryProvider.delete(new BinaryElementRequestImpl(sha1))) {
                            log.trace("Deleted {} binary", sha1);
                            result.binariesCleaned++;
                            result.totalSizeCleaned += bd.getLength();
                        } else {
                            log.error("Could not delete binary '{}'", sha1);
                        }
                    } else {
                        log.debug("Deleting '{}' has failed", sha1);
                    }
                } finally {
                    // remove delete protection (even if delete was not successful)
                    deleteProtectedBinaries.remove(sha1);
                    log.debug("Cleaning '{}' from ref. counter", sha1);
                }
            } else {
                Long timestamp = pair.getSecond();
                log.info("Binary {} has {} readers with last timestamp of {}", sha1, pair.getFirst().get(), timestamp);
                long trashTime = (System.currentTimeMillis() - timestamp) / 1000;
                if (trashTime > ConstantValues.gcReadersMaxTimeSecs.getLong()) {
                    log.info("Binary {} has reached it's max read time, removing it from ref. counter", sha1);
                    deleteProtectedBinaries.remove(sha1);
                } else {
                    log.info("Binary {} is being read! Not deleting.", sha1);
                }
            }
            return null;
        }
    }
}

