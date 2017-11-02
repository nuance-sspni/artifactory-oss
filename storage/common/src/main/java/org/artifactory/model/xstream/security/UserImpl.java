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

package org.artifactory.model.xstream.security;

import com.google.common.collect.ImmutableSet;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import org.apache.commons.lang.StringUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.sapi.security.SecurityConstants;
import org.artifactory.security.*;
import org.artifactory.security.props.auth.PropsTokenManager;
import org.artifactory.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@XStreamAlias("user")
public class UserImpl implements MutableUserInfo {
    private static final Logger log = LoggerFactory.getLogger(UserImpl.class);

    private String username;
    private String password;
    private String email;
    private String salt;
    private String genPasswordKey;
    private boolean admin;
    @XStreamOmitField
    private Boolean groupAdmin;
    private boolean enabled;
    private boolean updatableProfile;
    private boolean accountNonExpired;
    private boolean credentialsExpired;
    private boolean credentialsNonExpired;
    private boolean accountNonLocked;

    private String realm;
    private String privateKey;
    private String publicKey;
    private boolean transientUser;

    private Set<UserGroupInfo> groups = new HashSet<>(1);
    private Set<UserPropertyInfo> userPropertyInfos = new HashSet<>();

    private long lastLoginTimeMillis;
    private String lastLoginClientIp;

    private long lastAccessTimeMillis;
    private String lastAccessClientIp;

    private String bintrayAuth;

    private boolean locked;

    public UserImpl() {
    }

    public UserImpl(String username) {
        this.username = username;
    }

    public UserImpl(UserInfo user, boolean encryptProperties) {
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.salt = user.getSalt();
        this.email = user.getEmail();
        this.admin = user.isAdmin();
        this.groupAdmin = user.isGroupAdmin();
        this.enabled = user.isEnabled();
        this.updatableProfile = user.isUpdatableProfile();
        this.accountNonExpired = user.isAccountNonExpired();
        this.credentialsExpired = user.isCredentialsExpired();
        this.credentialsNonExpired = user.isCredentialsNonExpired();
        this.accountNonLocked = user.isAccountNonLocked();
        this.transientUser = user.isTransientUser();
        this.realm = user.getRealm();

        Set<UserGroupInfo> groups = user.getGroups();
        if (groups != null) {
            this.groups = new HashSet<>(groups);
        } else {
            this.groups = new HashSet<>(1);
        }
        Set<UserPropertyInfo> userPropertyInfos = user.getUserProperties();
        if (userPropertyInfos != null) {
            if (encryptProperties) {
                for (UserPropertyInfo userPropertyInfo : userPropertyInfos) {
                    String value  = userPropertyInfo.getPropValue();
                    if (shouldEncryptProperty(userPropertyInfo.getPropKey())) {
                        value = CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), userPropertyInfo.getPropValue());
                    }
                    putUserProperty(userPropertyInfo.getPropKey(), value);
                }
            } else {
                this.userPropertyInfos = new HashSet<>(userPropertyInfos);
            }
        } else {
            this.userPropertyInfos = new HashSet<>();
        }

        setPrivateKey(user.getPrivateKey());
        setPublicKey(user.getPublicKey());
        setGenPasswordKey(user.getGenPasswordKey());
        setLastLoginClientIp(user.getLastLoginClientIp());
        setLastLoginTimeMillis(user.getLastLoginTimeMillis());
        setBintrayAuth(user.getBintrayAuth());
        setLocked(user.isLocked());
        setCredentialsExpired(user.isCredentialsExpired());
        setCredentialsNonExpired(isCredentialsNonExpired());
    }

    private static boolean equalGroupsSet(Set<UserGroupInfo> s1, Set<UserGroupInfo> s2) {
        if (s1 == s2) {
            return true;
        }
        if (s1 == null || s2 == null) {
            return false;
        }
        if (s1.equals(s2)) {
            return true;
        }
        if (s1.size() != s2.size()) {
            return false;
        }
        for (UserGroupInfo g1 : s1) {
            if (!s2.contains(g1)) {
                return false;
            }
        }
        return true;
    }

    private static UserGroupInfo getDummyGroup(String groupName) {
        UserGroupInfo userGroupInfo = new UserGroupImpl(groupName, "whatever");
        return userGroupInfo;
    }

    private boolean shouldEncryptProperty(String key) {
        //TODO [YA] Refactor the SumoLogicTokenManager to be a PropsTokenManager to make the following redundant
        if ("sumologic.refresh.token".equals(key) || "sumologic.access.token".equals(key)) {
            return true;
        }
        Map<String, PropsTokenManager> beans = ContextHelper.get().beansForType(PropsTokenManager.class);
        if (beans != null) {
            return beans.values().stream()
                    .filter(manager -> StringUtils.equals(manager.getPropKey(), key))
                    .findAny()
                    .isPresent();
        }
        return false;
    }

    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    public void setCredentialsNonExpired(boolean credentialsNonExpired) {
        this.credentialsNonExpired = credentialsNonExpired;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public void setPassword(SaltedPassword saltedPassword) {
        this.password = saltedPassword.getPassword();
        this.salt = saltedPassword.getSalt();
    }

    @Override
    public String getSalt() {
        return salt;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String getPrivateKey() {
        return privateKey;
    }

    @Override
    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    @Override
    public String getPublicKey() {
        return publicKey;
    }

    @Override
    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    @Override
    public String getGenPasswordKey() {
        return genPasswordKey;
    }

    @Override
    public void setGenPasswordKey(String genPasswordKey) {
        this.genPasswordKey = genPasswordKey;
    }

    @Override
    public boolean isEffectiveAdmin() {
        if (admin) {
            return true;
        }
        if (groupAdmin == null) {
            if (CollectionUtils.notNullOrEmpty(groups)) {
                UserGroupService userGroupService = (ContextHelper.get() != null) ?
                        ContextHelper.get().beanForType(UserGroupService.class) : null;
                if (userGroupService != null) {
                    groupAdmin = groups.stream()
                            .map(group -> userGroupService.findGroup(group.getGroupName()))
                            .filter(Objects::nonNull)
                            .anyMatch(GroupInfo::isAdminPrivileges);
                } else {
                    log.warn("Artifactory context not found, can't determine effective admin status for user '"+username+"'.");
                }
            } else {
                groupAdmin = false;
            }
        }
        return groupAdmin != null && groupAdmin;
    }

    @Override
    public boolean isAdmin() {
        return admin;
    }

    @Override
    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    @Override
    public Boolean isGroupAdmin() {
        return groupAdmin;
    }

    @Override
    public void setGroupAdmin(boolean groupAdmin) {
        this.groupAdmin = groupAdmin;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isUpdatableProfile() {
        return updatableProfile;
    }

    @Override
    public void setUpdatableProfile(boolean updatableProfile) {
        this.updatableProfile = updatableProfile;
    }

    @Override
    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    @Override
    public void setAccountNonExpired(boolean accountNonExpired) {
        this.accountNonExpired = accountNonExpired;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public void setAccountNonLocked(boolean accountNonLocked) {
        this.accountNonLocked = accountNonLocked;
    }

    @Override
    public boolean isTransientUser() {
        return transientUser;
    }

    @Override
    public void setTransientUser(boolean transientUser) {
        this.transientUser = transientUser;
    }

    @Override
    public String getRealm() {
        return realm;
    }

    @Override
    public void setRealm(String realm) {
        this.realm = realm;
    }

    @Override
    public boolean isExternal() {
        return !SecurityConstants.DEFAULT_REALM.equals(realm);
    }

    @Override
    public boolean isCredentialsExpired() {
        return credentialsExpired;
    }

    @Override
    public void setCredentialsExpired(boolean credentialsExpired) {
        this.credentialsExpired = credentialsExpired;
    }

    @Override
    public boolean isAnonymous() {
        return (username != null && username.equalsIgnoreCase(ANONYMOUS));
    }

    @Override
    public boolean isInGroup(String groupName) {
        //Use the equals() behavior with a dummy userGroupInfo
        UserGroupInfo userGroupInfo = getDummyGroup(groupName);
        return getGroups().contains(userGroupInfo);
    }

    @Override
    public void addGroup(String groupName) {
        addGroup(groupName, SecurityConstants.DEFAULT_REALM);
    }

    @Override
    public void addGroup(String groupName, String realm) {
        UserGroupInfo userGroupInfo = new UserGroupImpl(groupName, realm);
        // group equality is currently using group name only, so make sure to remove existing group with the same name
        _groups().remove(userGroupInfo);
        _groups().add(userGroupInfo);
    }

    @Override
    public void removeGroup(String groupName) {
        //Use the equals() behavior with a dummy userGroupInfo
        UserGroupInfo userGroupInfo = getDummyGroup(groupName);
        _groups().remove(userGroupInfo);
    }

    /**
     * @return The _groups() names this user belongs to. Empty list if none.
     */
    @Override
    public Set<UserGroupInfo> getGroups() {
        return ImmutableSet.copyOf(_groups());
    }

    @Override
    public void setGroups(Set<UserGroupInfo> groups) {
        if (groups == null) {
            this.groups = new HashSet<>(1);
        } else {
            this.groups = new HashSet<>(groups);
        }
    }

    @Override
    public Set<UserPropertyInfo> getUserProperties() {
        if (userPropertyInfos == null) {
            this.userPropertyInfos = new HashSet<>();
        }
        return userPropertyInfos;
    }

    @Override
    public void setUserProperties(Set<UserPropertyInfo> userProperties) {
        userPropertyInfos = userProperties;
    }

    // Needed because XStream inject nulls :(
    private Set<UserGroupInfo> _groups() {
        if (groups == null) {
            this.groups = new HashSet<>(1);
        }
        return groups;
    }

    @Override
    public void putUserProperty(String key, String val) {
        if (userPropertyInfos == null) {
            this.userPropertyInfos = new HashSet<>();
        }
        userPropertyInfos.add(new UserProperty(key, val));
    }

    @Override
    public void setInternalGroups(Set<String> groups) {
        if (groups == null) {
            this.groups = new HashSet<>(1);
            return;
        }
        //Add groups with the default internal realm
        _groups().clear();
        for (String group : groups) {
            addGroup(group);
        }
    }

    @Override
    public long getLastLoginTimeMillis() {
        return lastLoginTimeMillis;
    }

    @Override
    public void setLastLoginTimeMillis(long lastLoginTimeMillis) {
        this.lastLoginTimeMillis = lastLoginTimeMillis;
    }

    @Override
    public String getLastLoginClientIp() {
        return lastLoginClientIp;
    }

    @Override
    public void setLastLoginClientIp(String lastLoginClientIp) {
        this.lastLoginClientIp = lastLoginClientIp;
    }

    @Override
    @Deprecated
    public long getLastAccessTimeMillis() {
        return lastAccessTimeMillis;
    }

    @Override
    @Deprecated
    public void setLastAccessTimeMillis(long lastAccessTimeMillis) {
        this.lastAccessTimeMillis = lastAccessTimeMillis;
    }

    @Override
    @Deprecated
    public String getLastAccessClientIp() {
        return lastAccessClientIp;
    }

    @Override
    @Deprecated
    public void setLastAccessClientIp(String lastAccessClientIp) {
        this.lastAccessClientIp = lastAccessClientIp;
    }

    @Override
    public String getBintrayAuth() {
        return bintrayAuth;
    }

    @Override
    public void setBintrayAuth(String bintrayAuth) {
        this.bintrayAuth = bintrayAuth;
    }

    /**
     * Compare the groups and login flags of the users to know if a force re-login is needed.
     *
     * @return true if users have same flags and groups, false otherwise.
     */
    @Override
    public boolean hasSameAuthorizationContext(UserInfo o) {
        if (o == null) {
            return false;
        }
        return isAdmin() == o.isAdmin()
                && isEnabled() == o.isEnabled()
                && hasSamePassword(o)
                && isAccountNonLocked() == o.isAccountNonLocked()
                && isCredentialsExpired() == o.isCredentialsExpired()
                && equalGroupsSet(getGroups(), o.getGroups());
    }

    private boolean hasSamePassword(UserInfo user) {
        if (password != null ? !password.equals(user.getPassword()) : user.getPassword() != null) {
            return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        UserImpl info = (UserImpl) o;

        return !(username != null ? !username.equals(info.username) : info.username != null);
    }

    @Override
    public int hashCode() {
        return (username != null ? username.hashCode() : 0);
    }

    @Override
    public boolean hasInvalidPassword() {
        return INVALID_PASSWORD.equals(getPassword());
    }

    /**
     * @return whether given user is locked
     */
    @Override
    public boolean isLocked() {
        return locked;
    }

    /**
     * @param locked whether given user is locked
     */
    @Override
    public void setLocked(boolean locked) {
        this.locked = locked;
    }
}