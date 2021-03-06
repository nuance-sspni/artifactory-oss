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

package org.artifactory.addon;

import com.google.common.collect.Lists;
import org.artifactory.addon.license.*;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.artifactory.addon.license.VerificationResult.error;
import static org.artifactory.addon.license.VerificationResult.valid;

/**
 * @author Yossi Shaul
 */
@Component
public class OssAddonsManager implements AddonsManager, AddonsWebManager {

    public static final String OSS_LICENSE_KEY_HASH = "Artifactory OSS";
    protected ArtifactoryContext context;
    @Autowired
    private ArtifactoryServersCommonService serversService;

    @Autowired
    private void setApplicationContext(ApplicationContext context) throws BeansException {
        this.context = (ArtifactoryContext) context;
    }

    @Override
    public <T extends Addon> T addonByType(Class<T> type) {
        return context.beanForType(type);
    }

    @Override
    public String getProductName() {
        return "Artifactory";
    }

    @Override
    public String getLicenseRequiredMessage(String licensePageUrl) {
        return "Add-ons are currently disabled.";
    }

    @Override
    public void onNoInstalledLicense(boolean userVisitedLicensePage, NoInstalledLicenseAction action) {
    }

    @Override
    public boolean isAdminPageAccessible() {
        AuthorizationService authService = context.beanForType(AuthorizationService.class);
        return authService.isAdmin() || authService.hasPermission(ArtifactoryPermission.MANAGE);
    }

    @Override
    public List<AddonInfo> getInstalledAddons(Set<String> excludedAddonKeys) {
        List<AddonInfo> addonInfos = Lists.newArrayList();
        for (AddonType addonType : AddonType.values()) {
            if (AddonType.AOL.equals(addonType)) {
                continue;
            }
            addonInfos.add(new AddonInfo(addonType.getAddonName(), addonType.getAddonDisplayName(), null,
                    AddonState.INACTIVATED, null, addonType.getDisplayOrdinal()));
        }

        Collections.sort(addonInfos);
        return addonInfos;
    }

    @Override
    public List<String> getEnabledAddonNames() {
        return Collections.emptyList();
    }

    @Override
    public boolean isLicenseInstalled() {
        return false;
    }

    @Override
    public boolean isAddonSupported(AddonType addonType) {
        return false;
    }

    @Override
    public boolean isHaLicensed() {
        return false;
    }

    @Override
    public boolean isTrialLicense() {
        return false;
    }

    @Override
    public boolean isXrayLicensed() {
        return false;
    }

    @Override
    public boolean xrayTrialSupported() {
        return false;
    }

    @Override
    public String getAddonProperty(String addonName, String addonKey) {
        throw new UnsupportedOperationException();
    }

    @Override
    public LicenseOperationStatus addAndActivateLicenses(Set<String> licenseKeys, boolean notifyListeners, boolean skipOnlineValidation) {
        throw new UnsupportedOperationException("Operation is not supported on Artifactory OSS");
    }

    @Override
    public LicenseOperationStatus addAndActivateLicense(String licenseKey, boolean notifyListeners, boolean skipOnlineValidation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getLicenseKey() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getLicensePerProduct(String productKey) {
        return null;
    }

    @Override
    public Date getLicenseValidUntil() {
        return null;
    }

    @Override
    public String getLicenseKeyHash() {
        return OSS_LICENSE_KEY_HASH;
    }

    @Override
    public boolean isProLicensed(String licenseKeyHash) {
        return false;
    }

    @Override
    public boolean isLicenseKeyHashHAType(String licenseKeyHash) {
        return false;
    }

    @Override
    public boolean lockdown() {
        return false;
    }

    @Override
    public List<ArtifactoryHaLicenseDetails> getClusterLicensesDetails() {
        ArtifactoryHaLicenseDetails oss = new ArtifactoryHaLicenseDetails("OSS", "", "", "", true, "", "");
        return Lists.newArrayList(oss);
    }

    @Override
    public ArtifactoryBaseLicenseDetails getProAndAolLicenseDetails() {
        return new ArtifactoryBaseLicenseDetails("OSS", "", "");
    }

    @Override
    public String getFooterMessage(boolean admin) {
        return null;
    }

    @Override
    public FooterMessage getLicenseFooterMessage() {
        return null;
    }

    @Override
    public String getLicenseTypeForProduct(String productName) {
        return "OSS";
    }

    @Override
    public boolean isLicenseExpired(String productName) {
        return false;
    }

    @Override
    public AddRemoveLicenseVerificationResult isLicenseKeyValid(String licenseKey, boolean excludeTrial, boolean validateOnline) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addLicenses(Set<String> licenses, LicenseOperationStatus status, boolean skipOnlineValidation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeLicenses(Set<String> licenses, LicenseOperationStatus status) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void releaseLicense() {

    }

    @Override
    public void activateLicense(Set<String> licenseHashesToIgnore, LicenseOperationStatus status, boolean notifyListeners,
            boolean initializing) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void resetLicenseCache() {
        //nop
    }

    @Override
    public boolean verifyExistingLicensesOnline(boolean force) {
        return false;
    }

    @Override
    public VerificationResult verifyAllArtifactoryServers(boolean running) {
        List<ArtifactoryServer> otherMembers = serversService.getOtherActiveMembers();
        if (!otherMembers.isEmpty()) {
            context.setOffline(); //leave it here
            throw new RuntimeException("Found active HA servers in DB, OSS is not supported in by active HA " +
                    "environment. Shutting down Artifactory.");
        }
        return valid;
    }

    @Override
    public ArtifactoryRunningMode getArtifactoryRunningMode() {
        return ArtifactoryRunningMode.OSS;
    }

    @Override
    public boolean isPartnerLicense() {
        return false;
    }

    @Override
    public void interceptResponse(ArtifactoryResponse response) {
    }

    @Override
    public void interceptRestResponse(ArtifactoryResponse response, String path) throws IOException {
    }
}
