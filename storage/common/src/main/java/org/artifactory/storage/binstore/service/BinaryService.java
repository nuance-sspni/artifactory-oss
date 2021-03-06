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

package org.artifactory.storage.binstore.service;

import org.artifactory.api.storage.BinariesInfo;
import org.artifactory.spring.ContextReadinessListener;
import org.artifactory.spring.ReloadableBean;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.jfrog.storage.binstore.exceptions.BinaryNotFoundException;
import org.jfrog.storage.binstore.ifc.model.BinaryProvidersInfo;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages binaries stored with SHA1 checksum as key.
 *
 * @author Yossi Shaul
 */
public interface BinaryService extends ContextReadinessListener, ReloadableBean {

    /**
     * Retrieve the readable stream with the bytes associated with the provided SHA1 checksum.
     *
     * @param sha1 the checksum key
     * @return the stream that should be closed by the user
     * @throws BinaryNotFoundException If the checksum does not exists in this store
     */
    InputStream getBinary(String sha1) throws BinaryNotFoundException;

    /**
     * Retrieve a dummy InputStream that will lock the sha1 for deletion until close is called.
     * It will also check for the real existence of the sha1 stream.
     * @param bi The full binary info
     * @return The dummy stream or null if sha1 does not exists in the binary provider
     * @throws BinaryNotFoundException if file being deleted
     */
    InputStream getBinary(BinaryInfo bi) throws BinaryNotFoundException;

    /**
     * Retrieve all the information (MD5, length) for this SHA1 key.
     *
     * @param sha1 the checksum key
     * @return The info if exists, null otherwise
     * @throws StorageException if an error happen accessing the binary store
     */
    @Nullable
    BinaryInfo findBinary(String sha1);

    /**
     * Retrieve a collection of binary information stored in this binary store,
     * based on a list of checksums keys.
     * The checksum type, SHA1 or MD5, will be determined by validation.
     * Any value that are invalid for all checksum type will be ignored.
     *
     * @param checksums list of checksum keys to look for
     * @return A distinct set of binaries information found, may be empty if nothing found
     * @throws StorageException if an error happen accessing the binary store
     */
    @Nonnull
    Set<BinaryInfo> findBinaries(@Nullable Collection<String> checksums);

    BinaryProvidersInfo<Map<String, String>> getBinaryProvidersInfo();

    /**
     * Add the whole content of the input stream in the binary store and close the input stream.
     * If the checksum for the whole stream already exists, the existing entry will be used.
     * The reference string is used for the reference count of this binary store.
     * Transitionally, the calling transaction will be suspended and all action on filesystem
     * will be done without DB TX opened. Then when binary data will need insert normal TX will be
     * done internally to this method call.
     * The input stream will not be consumed if the type is coming from here and the checksum is known.
     *
     * @param in the stream with all the bytes for the binary store
     * @return The info object for this binary entry
     * @throws java.io.IOException if the bytes cannot be read from the stream or saved in binary store
     */
    @Nonnull
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    BinaryInfo addBinary(InputStream in) throws IOException;

    /**
     * Check the sanity of this binary store.
     * DB, Filesystem or network access depending on the implementation.
     * Will throw exceptions if this binary store is not in full healthy state.
     */
    void ping();

    BinariesInfo getBinariesInfo();

    /**
     * Return the total size managed by this binary store.
     *
     * @return The total size or -1 if the binary does not supports fetch of the full size.
     */
    long getStorageSize();

    boolean isActivelyUsed(String sha1);

    List<String> getAndManageErrors();

    /**
     * Force optimization once even if there is no need for optimization (For immigration).
     */
    void forceOptimizationOnce();
}
