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

package org.artifactory.aql;

import com.google.common.collect.Sets;
import org.artifactory.aql.model.AqlItemTypeEnum;
import org.artifactory.aql.result.rows.AqlBaseFullRowImpl;
import org.artifactory.aql.result.rows.AqlItem;
import org.artifactory.aql.util.AqlUtils;
import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.fs.ItemInfo;
import org.artifactory.fs.MutableFileInfo;
import org.artifactory.fs.MutableFolderInfo;
import org.artifactory.repo.RepoPath;

import java.util.Date;
import java.util.Set;
import java.util.function.Function;

/**
 * Converter from Aql entities to other data objects.
 *
 * @author Yossi Shaul
 */
public abstract class AqlConverts {
    public static final Function<AqlItem, ItemInfo> toFileInfo = input -> {
            RepoPath repoPath = AqlUtils.fromAql((AqlBaseFullRowImpl) input);
            AqlItemTypeEnum type = input.getType();
            if (AqlItemTypeEnum.folder == type) {
                MutableFolderInfo folderInfo = InfoFactoryHolder.get().createFolderInfo(repoPath);
                folderInfo.setCreated(input.getCreated().getTime());
                folderInfo.setLastUpdated(input.getUpdated().getTime());
                folderInfo.setCreatedBy(input.getCreatedBy());
                Date modified = input.getModified();
                if (modified != null) {
                    folderInfo.setLastModified(modified.getTime());
                }
                folderInfo.setModifiedBy(input.getModifiedBy());
                return folderInfo;
            } else {
                MutableFileInfo fileInfo = InfoFactoryHolder.get().createFileInfo(repoPath);
                fileInfo.setSize(input.getSize());
                fileInfo.setCreated(input.getCreated().getTime());
                fileInfo.setLastUpdated(input.getUpdated().getTime());
                fileInfo.setCreatedBy(input.getCreatedBy());
                Date modified = input.getModified();
                if (modified != null) {
                    fileInfo.setLastModified(modified.getTime());
                }
                fileInfo.setModifiedBy(input.getModifiedBy());
                Set<ChecksumInfo> checksums = Sets.newHashSet();
                checksums.add(new ChecksumInfo(ChecksumType.md5, input.getOriginalMd5(), input.getActualMd5()));
                checksums.add(new ChecksumInfo(ChecksumType.sha1, input.getOriginalSha1(), input.getActualSha1()));
                fileInfo.setChecksums(checksums);
                return fileInfo;
            }
        };
}
