package org.artifactory.storage.db.security.service;

import org.artifactory.security.AclInfo;

import java.util.Map;
import java.util.Set;

/**
 * @author nadavy
 */
class AclCacheItem implements BasicCacheModel{

    // acl name to acl info.
    final Map<String, AclInfo> AclInfoMap;
    // Maps of user/group name to -> map of repo path to aclInfo
    final Map<String, Map<String, Set<AclInfo>>> UserResultMap;
    final Map<String, Map<String, Set<AclInfo>>> GroupResultMap;
    private long version;

    AclCacheItem(Map<String, AclInfo> aclInfoMap, Map<String, Map<String, Set<AclInfo>>> userResultMap,
            Map<String, Map<String, Set<AclInfo>>> groupResultMap) {
        this.AclInfoMap = aclInfoMap;
        this.UserResultMap = userResultMap;
        this.GroupResultMap = groupResultMap;
    }

    @Override
    public long getVersion() {
        return version;
    }

    @Override
    public void setVersion(long version) {
        this.version = version;
    }

    @Override
    public void destroy() {

    }
}