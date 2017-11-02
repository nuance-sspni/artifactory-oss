/*
 *
 * Copyright 2016 JFrog Ltd. All rights reserved.
 * JFROG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.artifactory.maven;

import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.DomainSensitiveField;
import org.artifactory.aql.result.rows.RowResult;
import org.artifactory.aql.util.AqlUtils;
import org.artifactory.repo.RepoPath;

import java.util.Date;

/**
 * @author gidis
 */
public class MavenMetaDataInfo implements RowResult {
    private String repo;
    private String path;
    private String name;
    private Date created;
    private RepoPath repoPath; // Lazy initialization
    private String version; // Lazy initialization


    @Override
    public void put(DomainSensitiveField field, Object value) {
        if (field.getField() == AqlPhysicalFieldEnum.itemRepo) {
            repo = (String) value;
        } else if (field.getField() == AqlPhysicalFieldEnum.itemName) {
            name = (String) value;
        } else if (field.getField() == AqlPhysicalFieldEnum.itemPath) {
            path = (String) value;
        } else if (field.getField() == AqlPhysicalFieldEnum.itemCreated) {
            created = (Date) value;
        } else {
            throw new RuntimeException("Unexpected field for MinimalItemInfo.class.");
        }
    }

    @Override
    public Object get(DomainSensitiveField field) {
        return null;
    }

    public RepoPath getRepoPath() {
        if (repoPath == null) {
            repoPath = AqlUtils.fromAql(repo, path, name);
        }
        return repoPath;
    }

    public void setRepoPath(RepoPath repoPath) {
        this.repoPath = repoPath;
    }

    public String getRepo() {
        return repo;
    }

    public void setRepo(String repo) {
        this.repo = repo;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public long getCreated() {
        return created.getTime();
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        if(version == null) {
            RepoPath parent = getRepoPath().getParent();
            if (parent != null) {
                version = parent.getName();
            }else {
                version = null;
            }
        }
        return version;
    }
}
