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

package org.artifactory.ui.rest.resource.admin.services.backups;

import org.artifactory.api.security.AuthorizationService;
import org.artifactory.ui.rest.model.admin.services.backups.Backup;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.ui.rest.service.admin.services.ServicesServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * @author Chen Keinan
 */
@Path("backup{id:(/[^/]+?)?}")
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class BackupResource extends BaseResource {

    @Autowired
    protected ServicesServiceFactory serviceFactory;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createBackup(Backup backup)
            throws Exception {
        return runService(serviceFactory.createBackupService(), backup);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateBackup(Backup backup)
            throws Exception {
        return runService(serviceFactory.updateBackupService(), backup);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getBackup()
            throws Exception {
        return runService(serviceFactory.getBackupService());
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteBackup()
            throws Exception {
        return runService(serviceFactory.deleteBackupService());
    }

    @Path("runnow")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response runNowBackup(Backup backup)
            throws Exception {
        return runService(serviceFactory.runNowBackupService(), backup);
    }
}
