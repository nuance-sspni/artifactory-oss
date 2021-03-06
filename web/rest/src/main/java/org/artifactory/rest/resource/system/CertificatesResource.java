package org.artifactory.rest.resource.system;

import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.webstart.ArtifactWebstartAddon;
import org.artifactory.addon.webstart.KeyStoreNotFoundException;
import org.artifactory.exception.InvalidCertificateException;
import org.artifactory.rest.ResponseModel;
import org.artifactory.rest.common.exception.BadRequestException;
import org.artifactory.rest.common.util.RestUtils;
import org.jfrog.security.ssl.CertificateHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.List;

/**
 * @author Shay Bagants
 */
public class CertificatesResource {
    private static final Logger log = LoggerFactory.getLogger(CertificatesResource.class);

    private final AddonsManager addonManager;

    public CertificatesResource(AddonsManager addonManager) {
        this.addonManager = addonManager;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCertificatesDetails() {
        List<CertificateInfoRestModel> certs = Lists.newArrayList();
        ArtifactWebstartAddon webstartAddon = addonManager.addonByType(ArtifactWebstartAddon.class);
        try {
            KeyStore keyStore = webstartAddon.getExistingKeyStore();
            if (keyStore != null) {
                Enumeration<String> aliases = keyStore.aliases();
                while (aliases.hasMoreElements()) {
                    String alias = aliases.nextElement();
                    if (alias.toLowerCase().startsWith(ArtifactWebstartAddon.SSL_CERT_ALIAS_PREFIX)) {
                        buildCertInfoModel(keyStore, alias, certs);
                    }
                }
            }
        } catch (KeyStoreNotFoundException e) {
            log.debug("No keyStore found. {}", e.getMessage());
        } catch (RuntimeException e) {
            log.debug("Failed to load KeyStore. {}", e.getMessage());
            log.trace("Failed to load KeyStore. {}", e);
        } catch (KeyStoreException e) {
            log.debug("No certificates found. {}", e.getMessage());
            log.trace("No certificates found. {}", e);
        }
        return Response.ok().entity(certs).build();
    }

    @DELETE
    @Path("{alias}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteCertificate(@PathParam("alias") String alias) {
        if (StringUtils.isBlank(alias)) {
            throw new BadRequestException("Please provide a certificate alias to delete");
        }
        ArtifactWebstartAddon webstartAddon = addonManager.addonByType(ArtifactWebstartAddon.class);
        boolean certExists = false;
        try {
            certExists = webstartAddon.hasKeyPair(ArtifactWebstartAddon.SSL_CERT_ALIAS_PREFIX + alias);
        } catch (RuntimeException e) {
            log.debug("Could not retrieve certificate from keystore. {}", e.getMessage());
        }
        if (certExists) {
            try {
                boolean deleted = webstartAddon.removeKeyPair(ArtifactWebstartAddon.SSL_CERT_ALIAS_PREFIX + alias);
                if (!deleted) {
                    throw new BadRequestException("Failed to remove certificate.");
                } else {
                    ResponseModel responseModel = new ResponseModel(Response.Status.OK.getStatusCode(), "The certificates were successfully deleted");
                    return Response.ok(responseModel).build();
                }
            } catch (Exception e) {
                log.debug("Failed to remove certificate. {}", e.getMessage());
                log.trace("Failed to remove certificate.", e);
                throw new BadRequestException("Failed to remove certificate. " + e.getMessage());
            }
        }
        throw new BadRequestException("Certificate '" + alias + "' not found.");
    }

    @POST
    @Path("{alias}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response addCertificate(String pem, @PathParam("alias") String alias) {
        if (StringUtils.isBlank(pem)) {
            throw new BadRequestException("PEM content cannot be empty");
        }
        if (StringUtils.isBlank(alias)) {
            throw new BadRequestException("Please provide an alias to store they certificate with");
        }
        ArtifactWebstartAddon webstartAddon = addonManager.addonByType(ArtifactWebstartAddon.class);
        try {
            webstartAddon.addPemCertificateToKeystore(pem, alias);
        } catch (UnsupportedOperationException e) {
            throw new BadRequestException("Client certificate is only supported on licensed Artifactory versions");
        } catch (InvalidCertificateException e) {
            throw new BadRequestException("Invalid PEM file. Make sure your PEM file includes Private key and Certificate in it.");
        }
        ResponseModel responseModel = new ResponseModel(Response.Status.OK.getStatusCode(), "The certificates were successfully installed");
        return Response.ok(responseModel).build();
    }

    private void buildCertInfoModel(KeyStore keyStore, String alias,
            List<CertificateInfoRestModel> certs)
            throws KeyStoreException {
        Certificate cert = keyStore.getCertificate(alias);
        if (cert != null) {
            String viewableAlias = alias.substring(ArtifactWebstartAddon.SSL_CERT_ALIAS_PREFIX.length());
            X509Certificate x509cert = (X509Certificate) cert;
            try {
                CertificateInfoRestModel certInfo = new CertificateInfoRestModel(
                        viewableAlias,
                        CertificateHelper.getCertificateSubjectCommonName(x509cert),
                        CertificateHelper.getCertificateIssuerCommonName(x509cert),
                        RestUtils.toIsoDateString(CertificateHelper.getIssuedAt(x509cert).getTime()),
                        RestUtils.toIsoDateString(CertificateHelper.getValidUntil(x509cert).getTime()),
                        CertificateHelper.getCertificateFingerprint(x509cert));
                certs.add(certInfo);
            } catch (CertificateEncodingException e) {
                log.info("Could not read certificate information for {}. {}", viewableAlias, e.getMessage());
            }
        }
    }
}
