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

package org.artifactory.storage.db.aql.sql.model;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.AqlTableFieldsEnum;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.artifactory.aql.model.AqlTableFieldsEnum.*;
import static org.artifactory.storage.db.aql.sql.model.SqlTableEnum.*;

/**
 * @author Gidi Shabat
 *
 * The Class extends the AqlFieldEnum.
 * The reason We split the AqlFiedEnumAnd into two parts  AqlFiedEnumAnd and AqlFieldExtensionEnum is to saparate the
 * database info from the common API.
 */
public enum AqlFieldExtensionEnum {
    // node
    artifactRepo(AqlPhysicalFieldEnum.itemRepo, nodes, repo, false),
    artifactPath(AqlPhysicalFieldEnum.itemPath, nodes, node_path, false),
    artifactName(AqlPhysicalFieldEnum.itemName, nodes, node_name, false),
    artifactCreated(AqlPhysicalFieldEnum.itemCreated, nodes, created, false),
    artifactModified(AqlPhysicalFieldEnum.itemModified, nodes, modified, true),
    artifactUpdated(AqlPhysicalFieldEnum.itemUpdated, nodes, updated, true),
    artifactCreatedBy(AqlPhysicalFieldEnum.itemCreatedBy, nodes, created_by, true),
    artifactModifiedBy(AqlPhysicalFieldEnum.itemModifiedBy, nodes, modified_by, true),
    artifactType(AqlPhysicalFieldEnum.itemType, nodes, node_type, false),
    artifactDepth(AqlPhysicalFieldEnum.itemDepth, nodes, depth, false),
    artifactNodeId(AqlPhysicalFieldEnum.itemId, nodes, node_id, false),
    artifactOriginalMd5(AqlPhysicalFieldEnum.itemOriginalMd5, nodes, md5_original, true),
    artifactActualMd5(AqlPhysicalFieldEnum.itemActualMd5, nodes, md5_actual, true),
    artifactOriginalSha1(AqlPhysicalFieldEnum.itemOriginalSha1, nodes, sha1_original, true),
    artifactActualSha1(AqlPhysicalFieldEnum.itemActualSha1, nodes, sha1_actual, true),
    artifactSize(AqlPhysicalFieldEnum.itemSize, nodes, bin_length, true),
    // stats
    artifactDownloaded(AqlPhysicalFieldEnum.statDownloaded, stats, last_downloaded, true),
    artifactDownloads(AqlPhysicalFieldEnum.statDownloads, stats, download_count, true),
    artifactDownloadedBy(AqlPhysicalFieldEnum.statDownloadedBy, stats, last_downloaded_by, true),
    statId(AqlPhysicalFieldEnum.statId, stats, node_id, false),
    // remote stats
    artifactRemoteDownloaded(AqlPhysicalFieldEnum.statRemoteDownloaded, stats_remote, last_downloaded, true),
    artifactRemoteDownloads(AqlPhysicalFieldEnum.statRemoteDownloads, stats_remote, download_count, true),
    artifactRemoteDownloadedBy(AqlPhysicalFieldEnum.statRemoteDownloadedBy, stats_remote, last_downloaded_by, true),
    artifactRemoteOrigin(AqlPhysicalFieldEnum.statRemoteOrigin, stats_remote, origin, true),
    artifactRemotePath(AqlPhysicalFieldEnum.statRemotePath, stats_remote, path, true),
    statRemoteId(AqlPhysicalFieldEnum.statRemoteId, stats_remote, node_id, false),
    // properties
    propertyKey(AqlPhysicalFieldEnum.propertyKey, node_props, prop_key, false),
    propertyValue(AqlPhysicalFieldEnum.propertyValue, node_props, prop_value, true),
    propertyId(AqlPhysicalFieldEnum.propertyId, node_props, node_id, false),
    // archive entries
    archiveEntryName(AqlPhysicalFieldEnum.archiveEntryName, archive_names, entry_name, false),
    archiveEntryPath(AqlPhysicalFieldEnum.archiveEntryPath, archive_paths, entry_path, false),
    archiveEntryPathId(AqlPhysicalFieldEnum.archiveEntryPathId, archive_paths, path_id, false),
    archiveEntryNameId(AqlPhysicalFieldEnum.archiveEntryNameId, archive_names, name_id, false),
    // builds
    moduleName(AqlPhysicalFieldEnum.moduleName, build_modules, module_name_id, false),
    moduleId(AqlPhysicalFieldEnum.moduleId, build_modules, module_id, false),
    buildDependencyName(AqlPhysicalFieldEnum.buildDependencyName, build_dependencies, dependency_name_id, false),
    buildDependencyScope(AqlPhysicalFieldEnum.buildDependencyScope, build_dependencies, dependency_scopes, false),
    buildDependencyType(AqlPhysicalFieldEnum.buildDependencyType, build_dependencies, dependency_type, false),
    buildDependencySha1(AqlPhysicalFieldEnum.buildDependencySha1, build_dependencies, sha1, false),
    buildDependencyMd5(AqlPhysicalFieldEnum.buildDependencyMd5, build_dependencies, md5, false),
    buildDependencyId(AqlPhysicalFieldEnum.buildDependencyId, build_dependencies, dependency_id, false),
    buildArtifactName(AqlPhysicalFieldEnum.buildArtifactName, build_artifacts, artifact_name, false),
    buildArtifactType(AqlPhysicalFieldEnum.buildArtifactType, build_artifacts, artifact_type, false),
    buildArtifactSha1(AqlPhysicalFieldEnum.buildArtifactSha1, build_artifacts, sha1, false),
    buildArtifactMd5(AqlPhysicalFieldEnum.buildArtifactMd5, build_artifacts, md5, false),
    buildArtifactId(AqlPhysicalFieldEnum.buildArtifactId, build_artifacts, artifact_id, false),
    buildPropertyKey(AqlPhysicalFieldEnum.buildPropertyKey, build_props, prop_key, false),
    buildPropertyValue(AqlPhysicalFieldEnum.buildPropertyValue, build_props, prop_value, false),
    buildPropertyId(AqlPhysicalFieldEnum.buildPropertyId, build_props, prop_id, false),
    buildPromotionCreated(AqlPhysicalFieldEnum.buildPromotionCreated,build_promotions,created,false),
    buildPromotionCreatedBy(AqlPhysicalFieldEnum.buildPromotionCreatedBy,build_promotions,created_by,true),
    buildPromotionStatus(AqlPhysicalFieldEnum.buildPromotionStatus,build_promotions,status,false),
    buildPromotionRepo(AqlPhysicalFieldEnum.buildPromotionRepo,build_promotions,repo,true),
    buildPromotionComment(AqlPhysicalFieldEnum.buildPromotionComment,build_promotions,promotion_comment,true),
    buildPromotionUserName(AqlPhysicalFieldEnum.buildPromotionUserName,build_promotions,ci_user,true),
    modulePropertyKey(AqlPhysicalFieldEnum.modulePropertyKey, module_props,prop_key,false),
    modulePropertyValue(AqlPhysicalFieldEnum.modulePropertyValue, module_props,prop_value,false),
    modulePropertyId(AqlPhysicalFieldEnum.modulePropertyId, module_props,prop_id,false),
    buildUrl(AqlPhysicalFieldEnum.buildUrl, builds, ci_url, false),
    buildName(AqlPhysicalFieldEnum.buildName, builds, build_name, false),
    buildNumber(AqlPhysicalFieldEnum.buildNumber, builds, build_number, false),
    buildCreated(AqlPhysicalFieldEnum.buildCreated, builds, created, false),
    buildCreatedBy(AqlPhysicalFieldEnum.buildCreatedBy, builds, created_by, true),
    buildModified(AqlPhysicalFieldEnum.buildModified, builds, modified, true),
    buildModifiedBy(AqlPhysicalFieldEnum.buildModifiedBy, builds, modified_by, true),
    build_id(AqlPhysicalFieldEnum.buildId, builds, AqlTableFieldsEnum.build_id, true);

    public final SqlTableEnum table;
    public final AqlTableFieldsEnum tableField;
    private final AqlPhysicalFieldEnum aqlField;
    private final boolean nullable;

    AqlFieldExtensionEnum(AqlPhysicalFieldEnum aqlField, SqlTableEnum table, AqlTableFieldsEnum tableField,
                          boolean nullable) {
        this.aqlField = aqlField;
        this.table = table;
        this.tableField = tableField;
        this.nullable = nullable;
    }

    public static AqlFieldExtensionEnum getExtensionFor(AqlPhysicalFieldEnum field) {
        for (AqlFieldExtensionEnum fieldExtensionEnum : values()) {
            if (fieldExtensionEnum.aqlField == field) {
                return fieldExtensionEnum;
            }
        }
        return null;
    }

    public AqlPhysicalFieldEnum getExtendedField() {
        return aqlField;
    }

    public boolean isNullable() {
        return nullable;
    }

    private static final AqlFieldExtensionEnum[] EMPTY_ARRAY = new AqlFieldExtensionEnum[0];
    private static final Map<SqlTableEnum, AqlFieldExtensionEnum[]> FIELDS_BY_TABLE;

    static {
        //Collect and map fields by domain
        Map<SqlTableEnum, List<AqlFieldExtensionEnum>> map = Maps.newHashMap();
        for (AqlFieldExtensionEnum field : values()) {
            List<AqlFieldExtensionEnum> fields = map.get(field.table);
            if (fields == null) {
                fields = Lists.newArrayList();
                map.put(field.table, fields);
            }
            fields.add(field);
        }
        //Convert map value to array (immutable, no need to convert to array afterwards, etc.)
        Map<SqlTableEnum, AqlFieldExtensionEnum[]> mapOfArrays = Maps.newHashMap();
        for (Map.Entry<SqlTableEnum, List<AqlFieldExtensionEnum>> entry : map.entrySet()) {
            List<AqlFieldExtensionEnum> fields = entry.getValue();
            mapOfArrays.put(entry.getKey(), fields.toArray(new AqlFieldExtensionEnum[fields.size()]));
        }
        FIELDS_BY_TABLE = Collections.unmodifiableMap(mapOfArrays);
    }

    static AqlFieldExtensionEnum[] getFieldsByTable(SqlTableEnum table) {
        AqlFieldExtensionEnum[] fields = FIELDS_BY_TABLE.get(table);
        if (fields == null) {
            return EMPTY_ARRAY;
        }
        return fields;
    }
}
