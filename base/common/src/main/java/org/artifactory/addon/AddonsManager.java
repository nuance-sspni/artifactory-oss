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

import org.artifactory.addon.license.*;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.state.model.ArtifactoryStateManager;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Provides addon factory by type.
 *
 * @author Yossi Shaul
 */
public interface AddonsManager {

    String ARTIFACTORY_PRODUCT_NAME = "artifactory";
    String XRAY_PRODUCT_NAME = "xray";
    // The hash of no licensed instance
    String NO_LICENSE_HASH = "da39a3ee5e6b4b0d3255bfef95601890afd807090";

    <T extends Addon> T addonByType(Class<T> type);

    List<AddonInfo> getInstalledAddons(@Nullable Set<String> excludedAddonKeys);

    List<String> getEnabledAddonNames();

    boolean isLicenseInstalled();

    /**
     * check if addon is supported
     * @param addonType - add on type to check
     * @return if true - addon is supported
     */
    boolean isAddonSupported(AddonType addonType);

    /**
     * Indicates whether there's a valid HA or Trial license installed.
     *
     * @return True if the license is either HA or Trial.
     */
    boolean isHaLicensed();

    /**
     * Indicates whether there's a valid Trial license installed. This method does not check if the license is expired.
     *
     * @return True if the license is either HA or Trial.
     */
    boolean isTrialLicense();

    /**
     * Indicates whether there's a valid Pro license and Xray license installed.
     *
     * @return True if the license is Pro and Xray
     */
    boolean isXrayLicensed();

    /**
     * Indicates whether the current license supports Xray trial
     */
    boolean xrayTrialSupported();

    /**
     * Returns the request property of the given addon name
     *
     * @param addonName Name of addon to query
     * @param addonKey  Key of requested property
     * @return Property value if addon name and property key were found. Null if not
     */
    String getAddonProperty(String addonName, String addonKey);

    /**
     * Add licenses to Artifactory license pool, and try to activate the licenses.
     * On Pro env, there can be one license only and 'add' will always replace the existing license in the pool and in
     * the artifactory.lic file and 'activate' will always activate the added license.
     * On HA env, 'add' means insert additional license to the cluster license pool. 'activate' will try to activate one
     * license (not necessarily the added one, there is a special logic to choose the license) on the current node,
     * but it might be that activate will do nothing (e.g. node already has a license).
     *
     * @param licenseKeys The license keys to add.
     * @param skipOnlineValidation Skip the online validation when adding the license key (this will be done on later anyway)w
     */
    LicenseOperationStatus addAndActivateLicenses(Set<String> licenseKeys, boolean notifyListeners, boolean skipOnlineValidation);

    /**
     * See {@link #addAndActivateLicenses}
     */
    LicenseOperationStatus addAndActivateLicense(String licenseKey, boolean notifyListeners, boolean skipOnlineValidation);

    /**
     * @return The currently installed license key.
     */
    String getLicenseKey();

    /**
     * @param productKey The product key
     * @return A base64 encoded string with the specific given product
     */
    String getLicensePerProduct(String productKey);

    Date getLicenseValidUntil();

    /**
     * Returns the hash of the license key (if installed) with an added char for indication of type
     * (<b>t</b>rial \ <b>c</b>ommercial).<br/>
     * <b>NOTE:</b> The returned hash will not be a valid one (inclusion of indication char).
     *
     * @return license hash + type indication
     */
    String getLicenseKeyHash();

    boolean isProLicensed(String licenseKeyHash);

    /**
     * check if license key hash is HA based on last Digit
     * @param licenseKeyHash - license key hash
     * @return if true key hash is HA license
     */
    boolean isLicenseKeyHashHAType(String licenseKeyHash);

    boolean lockdown();

    /**
     * Sends a "forbidden" response to the request if no valid license is installed
     *
     * @param response Response to intercept
     */
    void interceptResponse(ArtifactoryResponse response) throws IOException;

    /**
     * Sends a "forbidden" response to the rest request if no valid license is installed
     *
     * @param response Response to intercept
     * @param path
     */
    void interceptRestResponse(ArtifactoryResponse response, String path) throws IOException;

    List<ArtifactoryHaLicenseDetails> getClusterLicensesDetails();

    ArtifactoryBaseLicenseDetails getProAndAolLicenseDetails();

    String getProductName();

    /**
     * Verify current member is HA, all other members are HA and no duplicate licenses exist
     */
    VerificationResult verifyAllArtifactoryServers(boolean running);

    ArtifactoryRunningMode getArtifactoryRunningMode();

    boolean isPartnerLicense();

    /**
     * The caller of this method should cache the results for a short period of time. Currently, it is 10 seconds.
     * @return FooterMessage of the license status (expired, about to expire, HA running with no primary)
     */
    FooterMessage getLicenseFooterMessage();

    String getLicenseTypeForProduct(String productName);

    boolean isLicenseExpired(String productName);

    /**
     * Check if a license key is valid.
     * When checking an HA license, there is a flag of 'excludeTrial' to exclude trial licenses on HA validation.
     * @param licenseKey The license to check
     * @param excludeTrial On HA, when checking if license type is Enterprise, we can exclude (treat as invalid) trial
     * @param validateOnline Whether to validate the license online or not
     */
    AddRemoveLicenseVerificationResult isLicenseKeyValid(String licenseKey, boolean excludeTrial, boolean validateOnline);

    /**
     * Add licenses to Artifactory. On HA env (HA configured), we can add multiple licenses to the pool while on Pro
     * env, we can only add a single license.
     * This method does not responsible on activating the license on an instance, just add them to the pool.
     *
     * @param licenses Set of licenses to add
     */
    void addLicenses(Set<String> licenses, LicenseOperationStatus status, boolean skipOnlineValidation);

    /**
     * Remove licenses from Artifactory. On HA env (HA configured), we can remove one or multiple licenses from the
     * pool while on Pro env, we cannot remove a license at all.
     */
    void removeLicenses(Set<String> licenses, LicenseOperationStatus status);

    /**
     * Release the current license frmo this node. Should be used on HA env, when duplicate licenses found, release the
     * license from one of them
     */
    void releaseLicense();

    /**
     * Activate license on the current instance. On Pro env, we just activate the license from the single license pool
     * (artifactory.lic basically), while on HA env, we search for all available licenses in the pool which are not in
     * use by the other cluster members and activate one of these.
     * We use notifyListeners to decide if we need to notify all the listeners on new license activation, and
     * 'initializing' to tell if Artifactory is currently initializing (e.g {@link ArtifactoryStateManager#init()}), so
     * we will not reload the configuration, and not update the heartbeat.
     *
     * @param licenseHashesToIgnore The licenses to ignore (do not use) while trying to active a license
     * @param notifyListeners       Whether to notify listeners on the license activation or not
     * @param initializing          Whether Artifactory is at initialization process or not.
     */
    void activateLicense(Set<String> licenseHashesToIgnore, LicenseOperationStatus status, boolean notifyListeners,
            boolean initializing);

    /**
     * Resets and clears the license caches, in this instance.
     * Also causes a reload of the cache with the values that are in the cluster.licenses file that's currently
     * in its filesystem.
     */
    void resetLicenseCache();

    boolean verifyExistingLicensesOnline(boolean force);
}
