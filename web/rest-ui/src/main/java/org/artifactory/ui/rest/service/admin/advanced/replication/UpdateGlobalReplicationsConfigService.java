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

package org.artifactory.ui.rest.service.admin.advanced.replication;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.replication.GlobalReplicationsConfigDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.replication.GlobalReplicationConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author gidis
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class UpdateGlobalReplicationsConfigService implements RestService<GlobalReplicationConfig> {
    @Autowired
    CentralConfigService centralConfigService;

    @Override
    public void execute(ArtifactoryRestRequest<GlobalReplicationConfig> request, RestResponse response) {
        GlobalReplicationConfig model = request.getImodel();
        MutableCentralConfigDescriptor toSave = centralConfigService.getMutableDescriptor();
        GlobalReplicationsConfigDescriptor configDescriptor = new GlobalReplicationsConfigDescriptor();
        configDescriptor.setBlockPullReplications(model.isBlockPullReplications());
        configDescriptor.setBlockPushReplications(model.isBlockPushReplications());
        toSave.setGlobalReplicationConfig(configDescriptor);
        centralConfigService.saveEditedDescriptorAndReload(toSave);
    }
}
