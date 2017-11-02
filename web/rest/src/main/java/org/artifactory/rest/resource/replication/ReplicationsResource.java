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

package org.artifactory.rest.resource.replication;

import com.google.common.collect.Lists;
import org.artifactory.api.jackson.JacksonFactory;
import org.artifactory.api.rest.constant.ReplicationsRestConstants;
import org.artifactory.api.rest.replication.MultipleReplicationConfigRequest;
import org.artifactory.api.rest.replication.ReplicationConfigRequest;
import org.artifactory.api.rest.replication.ReplicationEnableDisableRequest;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.common.exception.BadRequestException;
import org.artifactory.rest.resource.BaseResource;
import org.artifactory.rest.services.ConfigServiceFactory;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.JsonMappingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.security.RolesAllowed;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;

/**
 * REST resource for configuring local and remote replication.
 *
 * @author mamo
 * @author Chen Keinan
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Path(ReplicationsRestConstants.ROOT)
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
public class ReplicationsResource extends BaseResource {

    @Context
    HttpServletRequest httpRequest;

    @Autowired
    ConfigServiceFactory configFactory;

    /**
     * get Single or multiple replications
     */
    @GET
    @Path("{repoKey: .+}")
    @Produces({ReplicationsRestConstants.MT_REPLICATION_CONFIG_REQUEST, ReplicationsRestConstants.MT_MULTI_REPLICATION_CONFIG_REQUEST, MediaType.APPLICATION_JSON})
    public Response getReplication() {
        return runService(configFactory.getReplication());
    }

    /**
     * Create or replace single replication
     * RTFACT-9055 - We now support JSON as an array OR as an object in order to support legacy.
     * The GET method return an array of configurations in case of Enterprise license, and single object json in case
     * of Pro license, while we do support sending a PUT request of an object replication settings as well (and not only as an array).
     */
    @PUT
    @Path("{repoKey: .+}")
    @Consumes({ReplicationsRestConstants.MT_REPLICATION_CONFIG_REQUEST, MediaType.APPLICATION_JSON})
    public Response addOrReplace() {
        try {
            // We get the inputStream in order to avoid filters parsing the request
            ServletInputStream inputStream = httpRequest.getInputStream();
            JsonParser jsonParser = JacksonFactory.createJsonParser(inputStream);
            try {
                // First we try parsing the request as a single 'ReplicationConfigRequest' object
                ReplicationConfigRequest replicationRequest = jsonParser.readValueAs(ReplicationConfigRequest.class);
                return runService(configFactory.createOrReplaceReplication(), replicationRequest);
            } catch (JsonMappingException e) {
                // If it wasn't a single object, try parsing the request as an array of ReplicationConfigRequest
                ReplicationConfigRequest[] replicationRequestArray = jsonParser.readValueAs(ReplicationConfigRequest[].class);
                ArrayList<ReplicationConfigRequest> replicationList = Lists.newArrayList(replicationRequestArray);
                // Create a MultipleReplicationConfigRequest from the list and send it to addOrReplaceMultiple
                MultipleReplicationConfigRequest multiRequest = createMultipleReplicationRequestObject(replicationList);
                return addOrReplaceMultiple(multiRequest);
            }

        } catch (IOException e) {
            // We use inner try/catch for a reason: the IOException might be thrown in multiple lines
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage())
                    .build();
        }
    }

    /**
     * Used to generate an instance of MultipleReplicationConfigRequest out of an array
     * compiled from multiple ReplicationConfigRequest.
     * Constraints:
     * cronExp will be the cronExp of the first object in the array
     * EnableEventReplication will be the EnableEventReplication of the first object in the array
     */
    private MultipleReplicationConfigRequest createMultipleReplicationRequestObject(
            ArrayList<ReplicationConfigRequest> configRequests) {
        MultipleReplicationConfigRequest request = new MultipleReplicationConfigRequest();

        if (configRequests.size() == 0) {
            throw new BadRequestException("Request is empty");
        }

        request.setReplications(configRequests);
        ReplicationConfigRequest replicationRequest = configRequests.get(0);
        if (replicationRequest.getUrl() == null || replicationRequest.getCronExp() == null) {
            throw new BadRequestException("One of the required fields are empty");
        }
        request.setCronExp(replicationRequest.getCronExp());
        request.setEnableEventReplication(replicationRequest.getEnableEventReplication());

        return request;
    }

    /**
     * create or replace multiple replication
     */
    @PUT
    @Consumes({ReplicationsRestConstants.MT_MULTI_REPLICATION_CONFIG_REQUEST, MediaType.APPLICATION_JSON})
    @Path("multiple{repoKey: .+}")
    public Response addOrReplaceMultiple(MultipleReplicationConfigRequest replicationRequest) {
        return runService(configFactory.createMultipleReplication(), replicationRequest);
    }

    /**
     * update existing single replication
     */
    @POST
    @Consumes({ReplicationsRestConstants.MT_REPLICATION_CONFIG_REQUEST, MediaType.APPLICATION_JSON})
    @Path("{repoKey: .+}")
    public Response updateReplications(ReplicationConfigRequest replicationRequest) {
        return runService(configFactory.updateReplication(), replicationRequest);
    }

    /**
     * update existing multi replication
     */
    @POST
    @Consumes({ReplicationsRestConstants.MT_MULTI_REPLICATION_CONFIG_REQUEST, MediaType.APPLICATION_JSON})
    @Path("multiple{repoKey: .+}")
    public Response updateMultipleReplications(MultipleReplicationConfigRequest replicationRequest) {
        return runService(configFactory.updateMultipleReplications(), replicationRequest);
    }

    /**
     * delete  existing single / multi replication
     */
    @DELETE
    @Path("{repoKey: .+}")
    public Response deleteReplications() {
        return runService(configFactory.deleteReplicationsService());
    }

    @POST
    @Consumes({MediaType.APPLICATION_JSON})
    @Path("enable")
    public Response enable(ReplicationEnableDisableRequest replicationEnableDisableRequest) {
        replicationEnableDisableRequest.setEnable(true);
        return runService(configFactory.enableDisableReplicationsService(), replicationEnableDisableRequest);
    }

    @POST
    @Consumes({MediaType.APPLICATION_JSON})
    @Path("disable")
    public Response disable(ReplicationEnableDisableRequest replicationEnableDisableRequest) {
        replicationEnableDisableRequest.setEnable(false);
        return runService(configFactory.enableDisableReplicationsService(), replicationEnableDisableRequest);
    }
}
