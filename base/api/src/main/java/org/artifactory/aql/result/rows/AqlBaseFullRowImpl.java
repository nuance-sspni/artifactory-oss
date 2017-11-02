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

package org.artifactory.aql.result.rows;

import org.artifactory.aql.model.AqlFieldEnum;
import org.artifactory.aql.model.AqlItemTypeEnum;
import org.artifactory.aql.model.AqlLogicalFieldEnum;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;

import java.util.Date;
import java.util.Map;

import static org.artifactory.aql.model.AqlDomainEnum.items;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.*;

/**
 * @author Gidi Shabat
 */
@QueryTypes(value = items,
        physicalFields = {itemId, itemType, itemRepo, itemPath, itemName, itemDepth, itemCreated, itemCreatedBy,
                itemModified, itemModifiedBy, itemUpdated, itemSize, itemActualSha1, itemOriginalSha1, itemActualMd5,
                // stats
                statDownloaded, statDownloads, statDownloadedBy,
                statRemoteDownloaded, statRemoteDownloads, statRemoteDownloadedBy, statRemoteOrigin, statRemotePath,
                // properties
                propertyKey, propertyValue,
                // archive entries
                archiveEntryName, archiveEntryPath,
                // builds
                moduleName, buildDependencyName, buildDependencyScope, buildDependencyType, buildDependencySha1,
                buildDependencyMd5, buildArtifactName, buildArtifactType, buildArtifactSha1, buildArtifactMd5,
                buildPropertyKey,
                buildPropertyValue, buildUrl, buildName, buildNumber, buildCreated, buildCreatedBy, buildModified,
                buildModifiedBy
        })
public class AqlBaseFullRowImpl
        implements AqlRowResult, FullRow, AqlItem, AqlBaseItem, AqlArchiveEntryItem, AqlBuildArtifact,
        AqlBuildDependency,
        AqlProperty, AqlBuild, AqlStatisticItem, AqlBuildProperty, AqlStatistics, AqlBuildModule, AqlBuildPromotion {

    Map<AqlFieldEnum, Object> map;

    public AqlBaseFullRowImpl(Map<AqlFieldEnum, Object> map) {
        this.map = map;
    }

    @Override
    public Date getCreated() {
        return (Date) map.get(AqlPhysicalFieldEnum.itemCreated);
    }

    @Override
    public Date getModified() {
        return (Date) map.get(AqlPhysicalFieldEnum.itemModified);
    }

    @Override
    public Date getUpdated() {
        return (Date) map.get(AqlPhysicalFieldEnum.itemUpdated);
    }

    @Override
    public String getCreatedBy() {
        return (String) map.get(AqlPhysicalFieldEnum.itemCreatedBy);
    }

    @Override
    public String getModifiedBy() {
        return (String) map.get(AqlPhysicalFieldEnum.itemModifiedBy);
    }

    @Override
    public long getStatId() {
        return (long) map.get(AqlPhysicalFieldEnum.statId);
    }

    @Override
    public Date getDownloaded() {
        return (Date) map.get(AqlPhysicalFieldEnum.statDownloaded);
    }

    @Override
    public int getDownloads() {
        return (int) map.get(AqlPhysicalFieldEnum.statDownloads);
    }

    @Override
    public String getDownloadedBy() {
        return (String) map.get(AqlPhysicalFieldEnum.statDownloadedBy);
    }

    @Override
    public Date getRemoteDownloaded() {
        return (Date) map.get(AqlPhysicalFieldEnum.statRemoteDownloaded);
    }

    @Override
    public int getRemoteDownloads() {
        return (int) map.get(AqlPhysicalFieldEnum.statRemoteDownloads);
    }

    @Override
    public String getRemoteDownloadedBy() {
        return (String) map.get(AqlPhysicalFieldEnum.statRemoteDownloadedBy);
    }

    @Override
    public String getRemoteOrigin() {
        return (String) map.get(AqlPhysicalFieldEnum.statRemoteOrigin);
    }

    @Override
    public String getRemotePath() {
        return (String) map.get(AqlPhysicalFieldEnum.statRemotePath);
    }

    @Override
    public AqlItemTypeEnum getType() {
        return (AqlItemTypeEnum) map.get(AqlPhysicalFieldEnum.itemType);
    }

    @Override
    public String getRepo() {
        return (String) map.get(AqlPhysicalFieldEnum.itemRepo);
    }

    @Override
    public String getPath() {
        return (String) map.get(AqlPhysicalFieldEnum.itemPath);
    }

    @Override
    public String getName() {
        return (String) map.get(AqlPhysicalFieldEnum.itemName);
    }

    @Override
    public long getSize() {
        return (long) map.get(AqlPhysicalFieldEnum.itemSize);
    }

    @Override
    public int getDepth() {
        return (int) map.get(AqlPhysicalFieldEnum.itemDepth);
    }

    @Override
    public long getNodeId() {
        return (long) map.get(AqlPhysicalFieldEnum.itemId);
    }

    @Override
    public String getOriginalMd5() {
        return (String) map.get(AqlPhysicalFieldEnum.itemOriginalMd5);
    }

    @Override
    public String getActualMd5() {
        return (String) map.get(AqlPhysicalFieldEnum.itemActualMd5);
    }

    @Override
    public String getOriginalSha1() {
        return (String) map.get(AqlPhysicalFieldEnum.itemOriginalSha1);
    }

    @Override
    public String getActualSha1() {
        return (String) map.get(AqlPhysicalFieldEnum.itemActualSha1);
    }

    @Override
    public String[] getVirtualRepos() {
        return (String[]) map.get(AqlLogicalFieldEnum.itemVirtualRepos);
    }

    @Override
    public String getKey() {
        return (String) map.get(AqlPhysicalFieldEnum.propertyKey);
    }

    @Override
    public String getValue() {
        return (String) map.get(AqlPhysicalFieldEnum.propertyValue);
    }

    @Override
    public String getEntryName() {
        return (String) map.get(AqlPhysicalFieldEnum.archiveEntryName);
    }

    @Override
    public String getEntryPath() {
        return (String) map.get(AqlPhysicalFieldEnum.archiveEntryPath);
    }

    @Override
    public String getBuildModuleName() {
        return (String) map.get(AqlPhysicalFieldEnum.moduleName);
    }

    @Override
    public Long getBuildModuleId() {
        return (Long) map.get(AqlPhysicalFieldEnum.moduleId);
    }

    @Override
    public String getBuildDependencyName() {
        return (String) map.get(AqlPhysicalFieldEnum.buildDependencyName);
    }

    @Override
    public String getBuildDependencyScope() {
        return (String) map.get(AqlPhysicalFieldEnum.buildDependencyScope);
    }

    @Override
    public String getBuildDependencyType() {
        return (String) map.get(AqlPhysicalFieldEnum.buildDependencyType);
    }

    @Override
    public String getBuildDependencySha1() {
        return (String) map.get(AqlPhysicalFieldEnum.buildDependencySha1);
    }

    @Override
    public String getBuildDependencyMd5() {
        return (String) map.get(AqlPhysicalFieldEnum.buildDependencyMd5);
    }

    @Override
    public String getBuildArtifactName() {
        return (String) map.get(AqlPhysicalFieldEnum.buildArtifactName);
    }

    @Override
    public String getBuildArtifactType() {
        return (String) map.get(AqlPhysicalFieldEnum.buildArtifactType);
    }

    @Override
    public String getBuildArtifactSha1() {
        return (String) map.get(AqlPhysicalFieldEnum.buildArtifactSha1);
    }

    @Override
    public String getBuildArtifactMd5() {
        return (String) map.get(AqlPhysicalFieldEnum.buildArtifactMd5);
    }

    @Override
    public String getBuildPropKey() {
        return (String) map.get(AqlPhysicalFieldEnum.buildPropertyKey);
    }

    @Override
    public String getBuildPropValue() {
        return (String) map.get(AqlPhysicalFieldEnum.buildPropertyValue);
    }

    @Override
    public String getBuildUrl() {
        return (String) map.get(AqlPhysicalFieldEnum.buildUrl);
    }

    @Override
    public String getBuildName() {
        return (String) map.get(AqlPhysicalFieldEnum.buildName);
    }

    @Override
    public String getBuildNumber() {
        return (String) map.get(AqlPhysicalFieldEnum.buildNumber);
    }

    @Override
    public Date getBuildCreated() {
        return (Date) map.get(AqlPhysicalFieldEnum.buildCreated);
    }

    @Override
    public String getBuildCreatedBy() {
        return (String) map.get(AqlPhysicalFieldEnum.buildCreatedBy);
    }

    @Override
    public Date getBuildModified() {
        return (Date) map.get(AqlPhysicalFieldEnum.buildModified);
    }

    @Override
    public String getBuildModifiedBy() {
        return (String) map.get(AqlPhysicalFieldEnum.buildModifiedBy);
    }

    @Override
    public Date getBuildPromotionCreated() {
        return (Date) map.get(AqlPhysicalFieldEnum.buildPromotionCreated);
    }

    @Override
    public String getBuildPromotionCreatedBy() {
        return (String) map.get(AqlPhysicalFieldEnum.buildPromotionCreatedBy);
    }

    @Override
    public String getBuildPromotionUser() {
        return (String) map.get(AqlPhysicalFieldEnum.buildPromotionUserName);
    }

    @Override
    public String getBuildPromotionComment() {
        return (String) map.get(AqlPhysicalFieldEnum.buildPromotionComment);
    }

    @Override
    public String getBuildPromotionStatus() {
        return (String) map.get(AqlPhysicalFieldEnum.buildPromotionStatus);
    }

    @Override
    public String getBuildPromotionRepo() {
        return (String) map.get(AqlPhysicalFieldEnum.buildPromotionRepo);
    }

    @Override
    public String toString() {
        return "AqlBaseFullRowImpl{map=" + map + "}";
    }
}
