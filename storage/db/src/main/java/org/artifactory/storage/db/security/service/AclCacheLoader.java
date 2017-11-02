package org.artifactory.storage.db.security.service;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.security.AceInfo;
import org.artifactory.security.AclInfo;
import org.artifactory.security.MutableAceInfo;
import org.artifactory.security.MutablePermissionTargetInfo;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.security.dao.AclsDao;
import org.artifactory.storage.db.security.dao.PermissionTargetsDao;
import org.artifactory.storage.db.security.dao.UserGroupsDao;
import org.artifactory.storage.db.security.entity.Ace;
import org.artifactory.storage.db.security.entity.Acl;
import org.artifactory.storage.db.security.entity.PermissionTarget;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * @author nadavy
 */
public class AclCacheLoader implements Callable<AclCacheItem> {

    private AclsDao aclsDao;
    private UserGroupsDao userGroupsDao;
    private PermissionTargetsDao permTargetsDao;

    AclCacheLoader(AclsDao aclsDao, UserGroupsDao userGroupsDao, PermissionTargetsDao permissionTargetsDao) {
        this.aclsDao = aclsDao;
        this.userGroupsDao = userGroupsDao;
        this.permTargetsDao = permissionTargetsDao;
    }

    /**
     * gets and updates AclCache from DB, when call by db promotion
     * This call creates user and group ACL cache mappers
     * key: user/group name
     * value: map of user/group repo keys of given user/group to respected repo key ACL (Access Control List)
     * ACL will still have all of their ACE (Access Control Entries)
     *
     * @return up-to-date AclCacheItem
     */
    @Override
    public AclCacheItem call() {
        Map<Long, String> allUsernamePerIds;
        Map<Long, String> allGroupNamePerIds;
        Map<Long, PermissionTarget> targetMap;
        Collection<Acl> allAcls;
        try {
            // get data from db
            allUsernamePerIds = userGroupsDao.getAllUsernamePerIds();
            allGroupNamePerIds = userGroupsDao.getAllGroupNamePerIds();
            targetMap = permTargetsDao.getAllPermissionTargets();
            allAcls = aclsDao.getAllAcls();
        } catch (SQLException e) {
            throw new StorageException("Could not load all Access Control List from DB due to:" + e.getMessage(), e);
        }
        Map<String, AclInfo> aclResultMap = Maps.newHashMapWithExpectedSize(allAcls.size());
        // user and group cache mappers. mapper key is username/group name
        // mapper value is a mapper of repo keys to a set of AclInfos
        Map<String, Map<String, Set<AclInfo>>> userResultMap = Maps.newHashMap();
        Map<String, Map<String, Set<AclInfo>>> groupResultMap = Maps.newHashMap();

        for (Acl acl : allAcls) {
            PermissionTarget permTarget = targetMap.get(acl.getPermTargetId());
            MutablePermissionTargetInfo permissionTarget = InfoFactoryHolder.get().createPermissionTarget(
                    permTarget.getName(), new ArrayList<>(permTarget.getRepoKeys()));
            permissionTarget.setIncludes(permTarget.getIncludes());
            permissionTarget.setExcludes(permTarget.getExcludes());
            ImmutableSet<Ace> dbAces = acl.getAces();
            HashSet<AceInfo> aces = new HashSet<>(dbAces.size());
            for (Ace dbAce : dbAces) {
                if (dbAce.isOnGroup()) {
                    String groupName = allGroupNamePerIds.get(dbAce.getGroupId());
                    addAceToMap(groupResultMap, acl, permissionTarget, aces, dbAce, groupName, true);
                } else {
                    String username = allUsernamePerIds.get(dbAce.getUserId());
                    addAceToMap(userResultMap, acl, permissionTarget, aces, dbAce, username, false);
                }
            }
            // populate allAcls master ACL list
            AclInfo aclInfo = InfoFactoryHolder.get().createAcl(permissionTarget, aces, acl.getLastModifiedBy());
            aclResultMap.put(permTarget.getName(), aclInfo);
        }
        return new AclCacheItem(aclResultMap, userResultMap, groupResultMap);
    }

    private void addAceToMap(Map<String, Map<String, Set<AclInfo>>> resultMap, Acl acl,
            MutablePermissionTargetInfo permissionTarget, HashSet<AceInfo> aces, Ace dbAce, String name, boolean isGroup) {
        MutableAceInfo ace;
        if (name != null) {
            ace = InfoFactoryHolder.get().createAce(name, isGroup, dbAce.getMask());
            if (ace != null) {
                aces.add(ace);
            }
            // populate group result map with given ace
            addPermissionTargetToResultMap(resultMap, name,
                    InfoFactoryHolder.get().createAcl(permissionTarget, aces, acl.getLastModifiedBy()));
        }
    }

    /**
     * Creates or add a user/group map to a aclInfo in AclCache user or group cache.
     *
     * @param resultMap group or user result map to add repokey/aclInfo to
     * @param key       username or group name for key
     * @param aclInfo   aclInfo to add for value
     */
    private void addPermissionTargetToResultMap(Map<String, Map<String, Set<AclInfo>>> resultMap, String key,
            AclInfo aclInfo) {
        Map<String, Set<AclInfo>> repoKeyMap = resultMap.computeIfAbsent(key, map -> Maps.newHashMap());
        List<String> repoKeys = aclInfo.getPermissionTarget().getRepoKeys();
        repoKeys.forEach(repoKey -> addRepoKeyToMap(repoKeyMap, repoKey, aclInfo));
    }

    /**
     * Add repo key to a user/group cache mapper, with an AclInfo
     *
     * @param map     specific user/group map of repo keys to aclInfos set
     * @param repoKey repokey to add
     * @param aclInfo aclInfo to add
     */
    private void addRepoKeyToMap(Map<String, Set<AclInfo>> map, String repoKey, AclInfo aclInfo) {
        Set<AclInfo> aclInfos = map.computeIfAbsent(repoKey, info -> Sets.newHashSet());
        aclInfos.add(aclInfo);
    }
}