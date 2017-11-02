/*
 *
 * Copyright 2016 JFrog Ltd. All rights reserved.
 * JFROG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.artifactory.repo.service.flexible.listeners;

import org.artifactory.addon.replication.ReplicationAddon;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.service.flexible.MoveCopyItemInfo;
import org.artifactory.repo.service.flexible.context.MoveCopyContext;

/**
 * @author gidis
 */
public class ReplicationMoveCopyListener implements MoveCopyListeners {
    private InternalRepositoryService repositoryService;
    private ReplicationAddon replicationAddon;

    public ReplicationMoveCopyListener(InternalRepositoryService repositoryService, ReplicationAddon replicationAddon) {
        this.repositoryService = repositoryService;
        this.replicationAddon = replicationAddon;
    }

    @Override
    public void notifyAfterMoveCopy(MoveCopyItemInfo itemInfo, MoveMultiStatusHolder status, MoveCopyContext context) {
        LocalRepo localRepo = repositoryService.localOrCachedRepositoryByKey(itemInfo.getSourceRepoPath().getRepoKey());
        if (localRepo != null) {
            replicationAddon.offerLocalReplicationDeleteEvent(itemInfo.getSourceRepoPath());
        }
    }

    @Override
    public void notifyBeforeMoveCopy(MoveCopyItemInfo itemInfo, MoveMultiStatusHolder status, MoveCopyContext context) {

    }

    @Override
    public boolean isInterested(MoveCopyItemInfo itemInfo, MoveCopyContext context) {
        return true;
    }
}
