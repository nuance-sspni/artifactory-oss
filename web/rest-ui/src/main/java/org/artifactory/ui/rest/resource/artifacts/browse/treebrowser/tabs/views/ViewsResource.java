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

package org.artifactory.ui.rest.resource.artifacts.browse.treebrowser.tabs.views;

import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.ViewArtifact;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.BaseArtifactInfo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.bower.BowerArtifactInfo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.composer.ComposerArtifactInfo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.debian.DebianArtifactMetadataInfo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.docker.DockerArtifactInfo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.docker.ancestry.DockerAncestryArtifactInfo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.gems.GemsArtifactInfo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.npm.NpmArtifactInfo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.nugetinfo.NugetArtifactInfo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.puppet.PuppetArtifactInfoModel;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.pypi.PypiArtifactInfo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.rpm.RpmArtifactInfo;
import org.artifactory.ui.rest.service.artifacts.browse.BrowseServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
@Path("views")
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ViewsResource extends BaseResource {

    @Autowired
    BrowseServiceFactory browseFactory;

    @Autowired
    @Qualifier("streamingRestResponse")
    @Override
    public void setArtifactoryResponse(RestResponse artifactoryResponse) {
        this.artifactoryResponse = artifactoryResponse;
    }

    @POST
    @Path("pom")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewPom(ViewArtifact viewArtifact)
            throws Exception {
        return runService(browseFactory.viewArtifactService(), viewArtifact);
    }

    @POST
    @Path("nuget")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewNuget(NugetArtifactInfo nugetArtifactInfo)
            throws Exception {
        return runService(browseFactory.nugetViewService(), nugetArtifactInfo);
    }

    @POST
    @Path("gems")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewGems(GemsArtifactInfo gemsArtifactInfo)
            throws Exception {
        return runService(browseFactory.gemsViewService(), gemsArtifactInfo);
    }

    @POST
    @Path("npm")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewGems(NpmArtifactInfo npmArtifactInfo)
            throws Exception {
        return runService(browseFactory.npmViewService(), npmArtifactInfo);
    }

    @POST
    @Path("rpm")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewRpm(RpmArtifactInfo rpmArtifactInfo)
            throws Exception {
        return runService(browseFactory.rpmViewService(), rpmArtifactInfo);
    }

    @POST
    @Path("debian")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewDebian(DebianArtifactMetadataInfo debianArtifactInfo)
            throws Exception {
        return runService(browseFactory.DebianViewService(), debianArtifactInfo);
    }

    @POST
    @Path("opkg")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewOpkg(DebianArtifactMetadataInfo debianArtifactInfo)
            throws Exception {
        return runService(browseFactory.DebianViewService(), debianArtifactInfo);
    }

    @POST
    @Path("pypi")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewPypi(PypiArtifactInfo pypiArtifactInfo)
            throws Exception {
        return runService(browseFactory.pypiViewService(), pypiArtifactInfo);
    }

    @POST
    @Path("puppet")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewPuppet(PuppetArtifactInfoModel puppetArtifactInfoModel)
            throws Exception {
        return runService(browseFactory.puppetViewService(), puppetArtifactInfoModel);
    }

    @POST
    @Path("bower")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewBower(BowerArtifactInfo bowerArtifactInfo)
            throws Exception {
        return runService(browseFactory.bowerViewService(), bowerArtifactInfo);
    }

    @POST
    @Path("conan")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewConan(BaseArtifactInfo artifactInfo)
            throws Exception {
        return runService(browseFactory.conanViewService(), artifactInfo);
    }

    @POST
    @Path("conan_package")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewConanPackage(BaseArtifactInfo artifactInfo)
            throws Exception {
        return runService(browseFactory.conanPackageViewService(), artifactInfo);
    }

    @POST
    @Path("docker")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewDocker(DockerArtifactInfo dockerArtifactInfo)
            throws Exception {
        return runService(browseFactory.dockerViewService(), dockerArtifactInfo);
    }

    @POST
    @Path("dockerv2")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewDockerV2(DockerArtifactInfo dockerArtifactInfo)
            throws Exception {
        return runService(browseFactory.dockerV2ViewService(), dockerArtifactInfo);
    }

    @POST
    @Path("dockerancestry")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewDockerAncestry(DockerAncestryArtifactInfo dockerAncestryArtifactInfo)
            throws Exception {
        return runService(browseFactory.dockerAncestryViewService(), dockerAncestryArtifactInfo);
    }

    @GET
    @Path("dockerproxy{id:(/[^/]+?)?}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewDockerProxyConfig() throws Exception {
        return runService(browseFactory.dockerProxyViewService());
    }

    @POST
    @Path("composer")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewComposer(ComposerArtifactInfo composerArtifactInfo)
            throws Exception {
        return runService(browseFactory.composerViewService(), composerArtifactInfo);
    }

    @POST
    @Path("chef")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewChef(BaseArtifactInfo artifactInfo)
            throws Exception {
        return runService(browseFactory.chefViewService(), artifactInfo);
    }



}