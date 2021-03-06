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

package org.artifactory.storage.db.fs.model;

import com.google.common.collect.Lists;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.exception.CancelException;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.fs.ItemInfo;
import org.artifactory.fs.MutableItemInfo;
import org.artifactory.fs.WatcherInfo;
import org.artifactory.md.Properties;
import org.artifactory.md.PropertiesInfo;
import org.artifactory.model.xstream.fs.PropertiesImpl;
import org.artifactory.repo.interceptor.StorageInterceptors;
import org.artifactory.sapi.fs.MutableVfsItem;
import org.artifactory.sapi.interceptor.DeleteContext;
import org.artifactory.security.AccessLogger;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.fs.repo.StoringRepo;
import org.artifactory.storage.fs.service.ItemMetaInfo;
import org.artifactory.storage.fs.service.NodeMetaInfoService;
import org.artifactory.storage.spring.StorageContextHelper;
import org.artifactory.util.PathValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Base class for the mutable folder and file.
 *
 * @author Yossi Shaul
 */
public abstract class DbMutableItem<T extends MutableItemInfo> extends DbFsItem<T> implements MutableVfsItem<T> {
    private static final Logger log = LoggerFactory.getLogger(DbMutableItem.class);

    /**
     * The mutable item info. All the item modifications done during this session are saved in this object.
     */
    protected final T mutableInfo;

    /**
     * Original item info captures the state of the item before any changes are done to the mutable item.
     * This is used to check if there are any pending changes to save on session commit. The original info is reset
     * with the mutable info after the item is saved.
     */
    private ItemInfo originalInfo;

    /**
     * Virgin item info captures the original state and does not change all along the session this mutable item
     * participates in.
     */
    private ItemInfo virginInfo;

    private Properties properties;

    /**
     * Virgin properties capture the original state and do not change all along the session this mutable item
     * participates in.
     */
    private PropertiesInfo virginProperties;

    private List<WatcherInfo> watchesToAdd;

    /**
     * True if this item is marked for deletion on session save
     */
    protected boolean markForDeletion;

    /**
     * True if this item was deleted from the db (after a session save)
     */
    private boolean deleted;

    /**
     * True if this item is marked in error state and should not be saved in the session.
     *
     * @see DbMutableItem#markError()
     */
    protected boolean inError;

    public DbMutableItem(StoringRepo repo, long id, T info) {
        super(repo, id, info);
        PathValidator.validate(info.getRepoPath().toPath());
        this.id = id;
        this.mutableInfo = info;
        originalInfo = InfoFactoryHolder.get().copyItemInfo(info);
        virginInfo = InfoFactoryHolder.get().copyItemInfo(info);
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setCreated(long created) {
        mutableInfo.setCreated(created);
    }

    @Override
    public void setCreatedBy(String user) {
        mutableInfo.setCreatedBy(user);
    }

    @Override
    public void setModified(long modified) {
        mutableInfo.setLastModified(modified);
    }

    @Override
    public void setModifiedBy(String user) {
        mutableInfo.setModifiedBy(user);
    }

    @Override
    public void setUpdated(long updated) {
        mutableInfo.setLastUpdated(updated);
    }

    @Override
    public void setProperties(Properties properties) {
        //Use change tracking ability of the properties
        Properties props = this.getProperties();
        if (props == properties) {
            return;
        }
        props.clear();
        props.putAll(properties);
        this.properties = props;
    }

    @Override
    public boolean isFile() {
        return !info.isFolder();
    }

    @Override
    public boolean isFolder() {
        return info.isFolder();
    }

    @Override
    public boolean isMarkedForDeletion() {
        return markForDeletion;
    }

    @Override
    public boolean isNew() {
        return id == DbService.NO_DB_ID;
    }

    @Override
    public boolean isDeleted() {
        return deleted;
    }

    @Override
    @Nonnull
    public Properties getProperties() {
        if (properties != null) {
            return properties;
        } else {
            return super.getProperties();
        }
    }

    @Override
    public boolean markOriginalProperties() {
        if (virginProperties == null) {
            virginProperties = new PropertiesImpl(getProperties());
            return true;
        }
        return false;
    }

    @Override
    @Nonnull
    public PropertiesInfo getOriginalProperties() {
        if (!isOriginalPropertiesAvailable()) {
            throw new IllegalStateException("Original properties are not available.");
        }
        return virginProperties;
    }

    @Override
    public boolean isOriginalPropertiesAvailable() {
        return virginProperties != null;
    }

    @Override
    public ItemInfo getOriginalInfo() {
        return virginInfo;
    }

    @Override
    public void addWatch(WatcherInfo watch) {
        if (watchesToAdd == null) {
            watchesToAdd = Lists.newArrayList();
        }
        watchesToAdd.add(watch);
    }

    @Override
    public void markError() {
        inError = true;
    }

    @Override
    public void releaseResources() {
        // nothing to release by default
    }

    @Override
    public boolean hasPendingChanges() {
        if (inError) {
            return false;
        }
        return isNew() || (markForDeletion && !deleted) || properties != null ||
                watchesToAdd != null || !mutableInfo.isIdentical(originalInfo);
    }

    /**
     * Call a given function with the virgin and current immutable item infos
     * @param function the function to apply
     * @param <R> the return type of the function
     * @return the return value of the function
     */
    <R> R doWithOriginalAndCurrentItemInfos(BiFunction<ItemInfo, ItemInfo, R> function) {
        return function.apply(virginInfo, mutableInfo);
    }

    @Override
    public void save() {
        if (inError) {
            throw new IllegalStateException("Attempt to save an item in error state: " + this);
        }

        if (isNew()) {
            if (!readyForPersistence()) {
                // session save might be called before an item is ready to be persisted in the database
                // in such case delay the save and expect a later session save to persist
                // if no other session save is called, an error will be emitted to the log on session remove
                log.debug("Item '{}' is not ready for persistence");
                return;
            }
            id = doCreateNode();
            if (properties != null && !properties.isEmpty()) { // no point to set empty props for new item
                getPropertiesService().setProperties(id, properties);
                createOrUpdateNodeMetaInfo();
            }
            if (watchesToAdd != null) {
                getWatchesService().addWatches(id, watchesToAdd);
            }
        } else if (markForDeletion) {
            log.debug("Deleting item: '{}'", getRepoPath());
            doDeleteInternal();
            getWatchesService().deleteWatches(id);
            getPropertiesService().deleteProperties(id);
            getNodeMetaInfoService().deleteMetaInfo(id);
            getFileService().deleteItem(id);
            deleted = true;
        } else {    // it's an update
            if (!mutableInfo.isIdentical(originalInfo)) {
                doUpdateNode();
            }
            if (properties != null) {
                getPropertiesService().setProperties(id, properties);
                if (!properties.isEmpty()) {
                    createOrUpdateNodeMetaInfo();
                } else {
                    getNodeMetaInfoService().deleteMetaInfo(id);
                }
            }
            if (watchesToAdd != null) {
                getWatchesService().addWatches(id, watchesToAdd);
            }
        }

        // reset original info
        resetAfterSave();
    }

    protected void resetAfterSave() {
        markForDeletion = false;
        watchesToAdd = null;
        properties = null;
        originalInfo = InfoFactoryHolder.get().copyItemInfo(mutableInfo);
    }

    protected void doDeleteInternal() {
        // allow override - file/folder can add specific cleanup before the node is removed
    }

    protected abstract long doCreateNode();

    protected abstract void doUpdateNode();

    protected abstract boolean readyForPersistence();

    protected void fillInfo(ItemInfo source) {
        setCreated(source.getCreated());
        setCreatedBy(source.getCreatedBy());
        setModified(source.getLastModified());
        setModifiedBy(source.getModifiedBy());
        setUpdated(source.getLastUpdated());
    }

    private void createOrUpdateNodeMetaInfo() {
        getNodeMetaInfoService().createOrUpdateNodeMetaInfo(id,
                new ItemMetaInfo(System.currentTimeMillis(), getAuthorizationService().currentUsername()));
    }

    private NodeMetaInfoService getNodeMetaInfoService() {
        return ContextHelper.get().beanForType(NodeMetaInfoService.class);
    }

    private AuthorizationService getAuthorizationService() {
        return ContextHelper.get().beanForType(AuthorizationService.class);
    }

    protected void fireBeforeDeleteEvent(DeleteContext ctx) throws CancelException {
        StorageInterceptors interceptors = StorageContextHelper.get().beanForType(StorageInterceptors.class);
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        interceptors.beforeDelete(this, statusHolder, ctx.isTriggeredByMove());
        if (statusHolder.isError()) {
            throw statusHolder.getCancelException();
        }
    }

    protected void fireAfterDeleteEvent(DeleteContext ctx) {
        StorageInterceptors interceptors = StorageContextHelper.get().beanForType(StorageInterceptors.class);
        interceptors.afterDelete(this, new BasicStatusHolder(), ctx);
        AccessLogger.deleted(getRepoPath());
    }
}
