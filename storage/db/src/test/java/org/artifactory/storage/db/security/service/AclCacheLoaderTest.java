package org.artifactory.storage.db.security.service;

import com.beust.jcommander.internal.Maps;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.artifactory.security.AceInfo;
import org.artifactory.security.AclInfo;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.security.PermissionTargetInfo;
import org.artifactory.storage.db.security.dao.AclsDao;
import org.artifactory.storage.db.security.dao.PermissionTargetsDao;
import org.artifactory.storage.db.security.dao.UserGroupsDao;
import org.artifactory.storage.db.security.entity.Ace;
import org.artifactory.storage.db.security.entity.Acl;
import org.artifactory.storage.db.security.entity.PermissionTarget;
import org.easymock.EasyMock;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.testng.Assert.assertTrue;

/**
 * UnitTest of AclCacheLoader, especially the call method
 * mock the dao to create AclCache
 *
 * @author nadavy
 */

@Test
public class AclCacheLoaderTest {

    private static final long READER_PERMISSION_TARGET = 1;
    private static final long DEPLOYERS_PERMISSION_TARGET = 2;

    private static final long REPO1_ACL = 1;
    private static final long ANY_ACL = 2;

    private static final long USER1 = 1;
    private static final long USER2 = 2;
    private static final long USER3 = 3;
    private static final long GROUP1 = 4;
    private static final long GROUP2 = 5;

    private static final String USERNAME1 = "user1";
    private static final String USERNAME2 = "user2";
    private static final String USERNAME3 = "user3";
    private static final String GROUPNAME1 = "group1";
    private static final String GROUPNAME2 = "group2";

    private static final String REPO1 = "repo1";
    private static final String REPO2 = "repo2";

    private AclCacheItem aclCacheItem;

    /**
     * Create DAO mocks, populate new AclCache and call AclCache.
     */
    @BeforeClass
    public void populateAclInfo() {
        Collection<Acl> aclInfos = Lists.newArrayList();
        aclInfos.add(getAnyAcl());
        aclInfos.add(getRepo1Acl());

        UserGroupsDao userGroupsDao = EasyMock.createMock(UserGroupsDao.class);
        AclsDao aclsDao = EasyMock.createMock(AclsDao.class);
        PermissionTargetsDao permTargetsDao = EasyMock.createMock(PermissionTargetsDao.class);

        Map<Long, String> usernamePerIds = getAllUsernamePerIds();
        Map<Long, String> groupnamePerIds = getAllGroupNamePerIds();

        try {
            EasyMock.expect(aclsDao.getAllAcls()).andReturn(aclInfos).anyTimes();
            EasyMock.expect(permTargetsDao.getAllPermissionTargets()).andReturn(getPermTargets()).anyTimes();
            EasyMock.expect(userGroupsDao.getAllUsernamePerIds()).andReturn(usernamePerIds).anyTimes();
            EasyMock.expect(userGroupsDao.getAllGroupNamePerIds()).andReturn(groupnamePerIds).anyTimes();
            EasyMock.replay(userGroupsDao, aclsDao, permTargetsDao);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        AclCacheLoader cacheLoader = new AclCacheLoader(aclsDao, userGroupsDao, permTargetsDao);
        aclCacheItem = cacheLoader.call();
    }

    /**
     * create readers ACL, with user1 and user2.
     */
    private Acl getAnyAcl() {
        Ace user1 = new Ace(1, ANY_ACL, ArtifactoryPermission.READ.getMask(), USER1, 0);
        Ace user2 = new Ace(2, ANY_ACL, ArtifactoryPermission.READ.getMask(), USER2, 0);
        Set<Ace> aces = Sets.newHashSet(user1, user2);

        Acl acl = new Acl(ANY_ACL, READER_PERMISSION_TARGET, 1, "me");
        acl.setAces(aces);
        return acl;

    }

    /**
     * create deployers ACL, with user2, user3 and group1
     */
    private Acl getRepo1Acl() {
        Ace user2 = new Ace(3, REPO1_ACL, ArtifactoryPermission.DEPLOY.getMask(), USER2, 0);
        Ace user3 = new Ace(4, REPO1_ACL, ArtifactoryPermission.DEPLOY.getMask(), USER3, 0);
        Ace group = new Ace(5, REPO1_ACL, ArtifactoryPermission.DEPLOY.getMask(), 0, GROUP1);
        Set<Ace> aces = Sets.newHashSet(user2, user3, group);

        Acl acl = new Acl(REPO1_ACL, DEPLOYERS_PERMISSION_TARGET, 1, "me");
        acl.setAces(aces);
        return acl;
    }

    /**
     * create permission targets for ACLs - readers on any local repo and deployers on repo1
     */
    private Map<Long, PermissionTarget> getPermTargets() {
        Map<Long, PermissionTarget> permissionTargetMap = Maps.newHashMap();
        Set<String> repoKeys = Sets.newHashSet(PermissionTargetInfo.ANY_LOCAL_REPO);
        PermissionTarget pmi = new PermissionTarget(READER_PERMISSION_TARGET, "readerT", "**", "");
        pmi.setRepoKeys(repoKeys);
        permissionTargetMap.put(READER_PERMISSION_TARGET, pmi);
        repoKeys = Sets.newHashSet(REPO1, REPO2);
        pmi = new PermissionTarget(DEPLOYERS_PERMISSION_TARGET, "deployT", "**", "a/**");
        pmi.setRepoKeys(repoKeys);
        permissionTargetMap.put(DEPLOYERS_PERMISSION_TARGET, pmi);
        return permissionTargetMap;
    }

    /**
     * return all the users for UserGroupDao mock
     */
    private Map<Long, String> getAllUsernamePerIds() {
        Map<Long, String> usernamePerIds = Maps.newHashMap();
        usernamePerIds.put(USER1, USERNAME1);
        usernamePerIds.put(USER2, USERNAME2);
        usernamePerIds.put(USER3, USERNAME3);
        return usernamePerIds;
    }

    /**
     * return all the groups for UserGroupDao mock
     */
    private Map<Long, String> getAllGroupNamePerIds() {
        Map<Long, String> groupNamePerIds = Maps.newHashMap();
        groupNamePerIds.put(GROUP1, GROUPNAME1);
        groupNamePerIds.put(GROUP2, GROUPNAME2);
        return groupNamePerIds;
    }

    /**
     * Assert the different AclCacheLoader caches- groups and users
     */
    public void testAclCacheLoader() {
        Map<String, Map<String, Set<AclInfo>>> groupResultMap = aclCacheItem.GroupResultMap;
        assertGroupMap(groupResultMap);

        Map<String, Map<String, Set<AclInfo>>> userResultMap = aclCacheItem.UserResultMap;
        assertUserMap(userResultMap);

    }

    /**
     * assert that user1 and user2 - read permission on any local repo
     * user2 and user3 2 - deploy permissions on repo1 (except a/**)
     */
    private void assertUserMap(Map<String, Map<String, Set<AclInfo>>> userResultMap) {
        assertTrue(userResultMap.size() == 3, "UserAclMap should have 3 users");

        Map<String, Set<AclInfo>> user1RepoToAclMap = userResultMap.get(USERNAME1);
        Map<String, Set<AclInfo>> user2RepoToAclMap = userResultMap.get(USERNAME2);
        Map<String, Set<AclInfo>> user3RepoToAclMap = userResultMap.get(USERNAME3);

        assertTrue(user1RepoToAclMap.size() == 1, "User1 should have permission on ANY");
        assertTrue(user2RepoToAclMap.size() == 3, "User2 should have ANY, REPO1 and REPO2");
        assertTrue(user3RepoToAclMap.size() == 2, "User3 should have REPO1 and REPO2");

        assertTrue(user1RepoToAclMap.get(PermissionTargetInfo.ANY_LOCAL_REPO) != null,
                "User1 should have permission on ANY");
        assertTrue(user2RepoToAclMap.get(PermissionTargetInfo.ANY_LOCAL_REPO) != null,
                "User1 should have permission on ANY");
        assertTrue(user2RepoToAclMap.get(REPO1) != null, "User2 should have permission on REPO1");
        assertTrue(user2RepoToAclMap.get(REPO2) != null, "User2 should have permission on REPO2");
        assertTrue(user3RepoToAclMap.get(REPO1) != null, "User3 should have permission on REPO1");
        assertTrue(user3RepoToAclMap.get(REPO2) != null, "User3 should have permission on REPO2");

        // check include/exclude
        assertTrue("a/**".equals(
                user3RepoToAclMap.get(REPO1).iterator().next().getPermissionTarget().getExcludesPattern()));
        assertTrue("**".equals(
                user3RepoToAclMap.get(REPO1).iterator().next().getPermissionTarget().getIncludesPattern()));
    }

    /**
     * assert that group1 is in 1 acl, has deploy permission on repo1 only
     * group2 should have any acls
     */
    private void assertGroupMap(Map<String, Map<String, Set<AclInfo>>> groupRepoToAclMap) {
        assertTrue(groupRepoToAclMap.size() == 1);
        Map<String, Set<AclInfo>> groupMapToAcl = groupRepoToAclMap.get(GROUPNAME1);
        Set<AclInfo> groupRepoAcls = groupMapToAcl.get(REPO1); // group1 has 1 acl, which consists only of REPO1
        assertTrue(groupRepoAcls != null, "GROUP1 shouldn't be null");
        assertTrue(groupRepoAcls.size() == 1, "GROUP1 should have only 1 ACL");
        Set<AceInfo> groupAces = groupRepoAcls.iterator().next().getAces();
        assertTrue(groupAces.size() == 2, "GROUP1 ACL should contain 2 ACEs"); // 2nd with user3
        assertTrue(groupRepoToAclMap.get(GROUPNAME2) == null, "GROUP2 don't have ACLs ");
    }
}
