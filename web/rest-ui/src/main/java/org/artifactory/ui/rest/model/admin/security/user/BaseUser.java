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

package org.artifactory.ui.rest.model.admin.security.user;

import org.apache.commons.lang.StringUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.rest.common.model.RestModel;
import org.artifactory.rest.common.util.JsonUtil;
import org.artifactory.security.UserConfigurationImpl;
import org.artifactory.security.UserInfo;

import java.util.List;
import java.util.Set;

/**
 * @author Chen Keinan
 */
public class BaseUser extends UserConfigurationImpl implements RestModel {

    private boolean proWithoutLicense;
    private Boolean canDeploy;
    private Boolean canManage;
    private Boolean preventAnonAccessBuild;
    private List<UserPermissions> permissionsList;
    private String externalRealmLink;
    private Boolean anonAccessEnabled;
    private boolean existsInDB;
    private boolean requireProfileUnlock;
    private boolean requireProfilePassword;
    private Boolean locked;
    private Boolean credentialsExpired;
    private Integer currentPasswordValidFor;
    private Integer numberOfGroups;
    private Integer numberOfPermissions;

    public BaseUser(){}

    public BaseUser(UserInfo user) {
        long lastLoginTimeMillis = user.getLastLoginTimeMillis();
        if (lastLoginTimeMillis > 0) {
            this.setLastLoggedIn(ContextHelper.get().getCentralConfig().getDateFormatter().print(lastLoginTimeMillis));
            this.setLastLoggedInMillis(lastLoginTimeMillis);
        }
        this.setRealm(user.getRealm());
        this.setAdmin(user.isAdmin());
        this.setGroupAdmin(user.isGroupAdmin());
        this.setEmail(user.getEmail());
        this.setName(user.getUsername());
        this.setProfileUpdatable(user.isUpdatableProfile());
        this.setLocked(user.isLocked());
        this.setCredentialsExpired(user.isCredentialsExpired());
        if (StringUtils.isBlank(user.getPassword())) {
            this.setInternalPasswordDisabled(true);
        }
    }

    public void setProWithoutLicense(boolean proWithoutLicense) {
        this.proWithoutLicense = proWithoutLicense;
    }

    public boolean isProWithoutLicense() {
        return proWithoutLicense;
    }

    public BaseUser (String userName,boolean admin){
        super.setAdmin(admin);
        super.setName(userName);
    }

    public Boolean isCanDeploy() {
        return canDeploy;
    }

    public void setCanDeploy(Boolean canDeploy) {
        this.canDeploy = canDeploy;
    }

    public Boolean isCanManage() {
        return canManage;
    }

    public void setCanManage(Boolean canManage) {
        this.canManage = canManage;
    }

    public Boolean isPreventAnonAccessBuild() {
        return preventAnonAccessBuild;
    }

    public void setPreventAnonAccessBuild(Boolean preventAnonAccessBuild) {
        this.preventAnonAccessBuild = preventAnonAccessBuild;
    }

    public List<UserPermissions> getPermissionsList() {
        return permissionsList;
    }

    public void setPermissionsList(List<UserPermissions> permissionsList) {
        this.permissionsList = permissionsList;
    }


    public String getExternalRealmLink() {
        return externalRealmLink;
    }

    public void setExternalRealmLink(String externalRealmLink) {
        this.externalRealmLink = externalRealmLink;
    }

    public Boolean getAnonAccessEnabled() {
        return anonAccessEnabled;
    }

    public void setAnonAccessEnabled(Boolean anonAccessEnabled) {
        this.anonAccessEnabled = anonAccessEnabled;
    }

    public Boolean getLocked() {
        return locked;
    }

    public void setLocked(Boolean locked) {
        this.locked = locked;
    }

    public boolean isExistsInDB() { return existsInDB; }

    public void setExistsInDB(boolean existsInDB) { this.existsInDB = existsInDB; }

    public boolean isRequireProfileUnlock() {
        return requireProfileUnlock;
    }

    public boolean isRequireProfilePassword() {
        return requireProfilePassword;
    }

    public void setRequireProfilePassword(boolean requireProfilePassword) {
        this.requireProfilePassword = requireProfilePassword;
    }

    public void setRequireProfileUnlock(boolean requireProfileUnlock) {
        this.requireProfileUnlock = requireProfileUnlock;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }

    public Boolean getCredentialsExpired() {
        return credentialsExpired;
    }

    public void setCredentialsExpired(Boolean credentialsExpired) {
        this.credentialsExpired = credentialsExpired;
    }

    /**
     * @return number of days till password should be changed
     */
    public Integer getCurrentPasswordValidFor() {
        return currentPasswordValidFor;
    }

    /**
     * @param currentPasswordValidFor number of days till password should be changed
     */
    public void setCurrentPasswordValidFor(Integer currentPasswordValidFor) {
        this.currentPasswordValidFor = currentPasswordValidFor;
    }

    public Integer getNumberOfGroups() {
        if (numberOfGroups == null) {
            Set<String> groups = getGroups();
            return groups == null ? 0 : groups.size();
        }
        return numberOfGroups;
    }

    public void setNumberOfGroups(Integer numberOfGroups) {
        this.numberOfGroups = numberOfGroups;
    }

    public Integer getNumberOfPermissions() {
        if (numberOfPermissions == null) {
            List<UserPermissions> permissionsList = getPermissionsList();
            return permissionsList == null ? 0 : permissionsList.size();
        }
        return numberOfPermissions;
    }

    public void setNumberOfPermissions(Integer numberOfPermissions) {
        this.numberOfPermissions = numberOfPermissions;
    }
}
