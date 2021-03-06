package org.artifactory.ui.rest.resource.admin.security.ssl;

import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.ui.rest.model.admin.security.ssl.CertificateDeleteModel;
import org.artifactory.ui.rest.model.admin.security.ssl.CertificateModel;
import org.artifactory.ui.rest.service.admin.security.SecurityServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * @author Shay Bagants
 */
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)

@Path("admin/security/certificates")
public class SslCertificatesResource extends BaseResource {
    private static final Logger log = LoggerFactory.getLogger(SslCertificatesResource.class);

    @Autowired
    private SecurityServiceFactory securityFactory;

    @POST
    @Path("add")
    public Response addCertificate(CertificateModel certificateModel) {
        return runService(securityFactory.addPemClientCertificate(), certificateModel);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("getAllCertificates")
    public Response getAllCertificatesData() {
        return runService(securityFactory.getCertificatesData());
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("details")
    public Response getCertificateDetails() {
        return runService(securityFactory.getCertificateDetails());
    }

    @POST
    @Path("delete")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response deleteCertificate(CertificateDeleteModel certificateDeleteModel) {
        return runService(securityFactory.removeCertificate(), certificateDeleteModel);
    }
}
