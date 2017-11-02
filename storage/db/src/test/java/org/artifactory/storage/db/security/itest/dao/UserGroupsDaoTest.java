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

package org.artifactory.storage.db.security.itest.dao;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Multimap;
import org.artifactory.api.security.PasswordExpiryUser;
import org.artifactory.security.SaltedPassword;
import org.artifactory.storage.db.security.dao.UserGroupsDao;
import org.artifactory.storage.db.security.dao.UserPropertiesDao;
import org.artifactory.storage.db.security.entity.Group;
import org.artifactory.storage.db.security.entity.User;
import org.artifactory.storage.db.security.entity.UserGroup;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static org.testng.Assert.*;

/**
 * Tests {@link org.artifactory.storage.db.security.dao.UserGroupsDao}.
 *
 * @author freds
 */
@Test
public class UserGroupsDaoTest extends SecurityBaseDaoTest {

    @Autowired
    private UserPropertiesDao userPropsDao;

    @BeforeClass
    public void setup() {
        importSql("/sql/user-group.sql");
    }

    public void testCreateGroup() throws SQLException {
        int res = userGroupsDao.createGroup(new Group(4L, "g4", "Test G4", false, "test realm", "test att", false));
        assertEquals(res, 1);
    }

    /**
     * admin, u3 - admins with no groups
     * u2 - admin with non admin group
     * adminByGroup - just group admin
     * adminWithAdminGroup - admin with group group + non admin groups
     */
    public void testGroupAdmin() throws SQLException {
        ImmutableSet<String> group16users = ImmutableSet.of("adminWithAdminGroup", "adminWithAdminGroup");
        ImmutableSet<String> admins = ImmutableSet.of("adminWithAdminGroup", "adminWithAdminGroup", "admin",
                "u2", "u3");
        List<String> allUserNamesInGroup = userGroupsDao.findAllUserNamesInGroup(16);
        assertEquals(allUserNamesInGroup.size(),2);
        assertTrue(allUserNamesInGroup.containsAll(group16users));
        List<String> allAdminGroups = userGroupsDao.findAllAdminGroups();
        assertEquals(allAdminGroups.size(), 1);
        assertTrue(allAdminGroups.contains("admins"));
        ImmutableSet<String> notAdmins = ImmutableSet.of("u1", "anonymous", "userNoGroups");
        Collection<User> allNonAdminsUsers = userGroupsDao.getAllUsers(false);
        List<String> allNonAdminUserNames = userGroupsDao.getAllNonAdminUserNames();
        assertTrue(allNonAdminUserNames.containsAll(notAdmins));
        List<String> usernames = allNonAdminsUsers.stream().map(User::getUsername).collect(Collectors.toList());
        assertTrue(usernames.containsAll(notAdmins));
        boolean anyAdminName = admins.stream().anyMatch(usernames::contains);
        assertFalse(anyAdminName);
        List<String> allAdminUserNames = userGroupsDao.getAllAdminUserNames();
        assertEquals(allAdminUserNames.size(),5);
        assertTrue(allAdminUserNames.containsAll(admins));
    }

    public void getAllGroupIdsToNames() throws SQLException {
        Map<String, Long> allGroupIdsToNames = userGroupsDao.getAllGroupIdsToNames();
        assertEquals(allGroupIdsToNames.size(), 5);
    }

    public void getAllUsersInGroups() throws SQLException {
        Multimap<Long, Long> allUsersInGroups = userGroupsDao.getAllUsersInGroups();
        Collection<Long> userInAdminGroup = allUsersInGroups.get(16L);
        assertEquals(userInAdminGroup.size(), 2);
    }

    @Test(dependsOnMethods = "testCreateGroup")
    public void testUpdateGroup() throws SQLException {
        Group group = userGroupsDao.findGroupById(4L);
        assertNotNull(group);
        assertEquals(group.getGroupId(), 4L);
        assertEquals(group.getGroupName(), "g4");
        Group modGroup = new Group(group.getGroupId(), group.getGroupName(), group.getDescription() + "m",
                !group.isNewUserDefault(), group.getRealm() + "m", group.getRealmAttributes() + "m",
                group.isAdminPrivileges());
        int res = userGroupsDao.updateGroup(modGroup);
        assertEquals(res, 1);
        Group modReadGroup = userGroupsDao.findGroupById(4L);
        assertEquals(modReadGroup.getGroupId(), 4L);
        assertTrue(modReadGroup.isIdentical(modGroup));
    }

    @Test(dependsOnMethods = "testUpdateGroup")
    public void testWrongUpdateGroupName() throws SQLException {
        Group group = userGroupsDao.findGroupById(4L);
        assertNotNull(group);
        assertEquals(group.getGroupId(), 4L);
        assertEquals(group.getGroupName(), "g4");
        Group modGroup = new Group(group.getGroupId(), group.getGroupName() + "changingName",
                group.getDescription(),
                group.isNewUserDefault(), group.getRealm(), group.getRealmAttributes(), group.isAdminPrivileges());
        int res = userGroupsDao.updateGroup(modGroup);
        assertEquals(res, 0);
        Group modReadGroup = userGroupsDao.findGroupById(4L);
        assertEquals(modReadGroup.getGroupId(), 4L);
        assertFalse(modReadGroup.isIdentical(modGroup));
        assertTrue(modReadGroup.isIdentical(group));
    }

    @Test(dependsOnMethods = "testWrongUpdateGroupName")
    public void testDeleteGroup() throws SQLException {
        assertEquals(userGroupsDao.deleteGroup("g4"), 1);
        assertNull(userGroupsDao.findGroupById(4L));
    }

    @Test
    public void testCreateUser() throws SQLException {
        long now = System.currentTimeMillis();
        final User u4 = new User(4L, "u4",
                "4pass", "salty", "4@mail.com", "genPass",
                false, true, false,
                "realmy", "privKey", "pubKey",
                now - 10000L, "10.0.0.1",
                "johndoe:sdfjshf23SDF3kljsdfXVXD324", false, false);
        u4.setGroups(new HashSet<UserGroup>(1));
        int res = userGroupsDao.createUser(u4);
        assertEquals(res, 1);
        User user = userGroupsDao.findUserById(4L);
        assertNotNull(user);
        assertTrue(user.isIdentical(u4));
    }

    @Test(dependsOnMethods = "testCreateUser")
    public void testCreateUserWithNulls() throws SQLException {
        long now = System.currentTimeMillis();
        final User u5 = new User(5L, "u5",
                null, "", "", "",
                false, true, false,
                "", "", "",
                now - 10000L, "",
                "", false, false);
        u5.setGroups(new HashSet<UserGroup>(1));
        int res = userGroupsDao.createUser(u5);
        assertEquals(res, 1);
        User user = userGroupsDao.findUserById(5L);
        assertNotNull(user);
        assertFalse(user.isIdentical(u5));
        assertEquals(user.getPassword(), "");
        assertNull(user.getSalt());
        assertNull(user.getEmail());
        assertNull(user.getGenPasswordKey());
        assertNull(user.getRealm());
        assertNull(user.getPrivateKey());
        assertNull(user.getPublicKey());
        assertNull(user.getLastLoginClientIp());
        assertNull(user.getBintrayAuth());
    }

    @Test(dependsOnMethods = "testUnLockAllUsers")
    public void testUpdateUser() throws SQLException {
        User user = userGroupsDao.findUserById(4L);
        assertNotNull(user);
        assertEquals(user.getUserId(), 4L);
        assertEquals(user.getUsername(), "u4");
        User modUser = new User(user.getUserId(), user.getUsername(),
                user.getPassword() + "m", user.getSalt() + "m", user.getEmail() + "m",
                user.getGenPasswordKey() + "m", !user.isAdmin(), !user.isEnabled(), !user.isUpdatableProfile(),
                user.getRealm() + "m", user.getPrivateKey() + "m", user.getPublicKey() + "m",
                user.getLastLoginTimeMillis() + 2L, user.getLastLoginClientIp() + "0",
                "gogo:sdflkjsdfksjdfSEDFSDF2345325DFG", false, false);
        modUser.setGroups(user.getGroups());
        assertEquals(userGroupsDao.updateUser(modUser), 1);
        User readUser = userGroupsDao.findUserById(4L);
        assertNotNull(readUser);
        assertTrue(readUser.isIdentical(modUser));
    }

    @Test(dependsOnMethods = "testUpdateUser")
    public void testWrongUpdateUser() throws SQLException {
        User user = userGroupsDao.findUserById(4L);
        assertNotNull(user);
        assertEquals(user.getUserId(), 4L);
        assertEquals(user.getUsername(), "u4");
        User modUser = new User(user.getUserId(), user.getUsername() + "modified",
                user.getPassword(), user.getSalt(), user.getEmail(),
                user.getGenPasswordKey(), user.isAdmin(), user.isEnabled(), user.isUpdatableProfile(),
                user.getRealm(), user.getPrivateKey(), user.getPublicKey(),
                user.getLastLoginTimeMillis(), user.getLastLoginClientIp(),
                "sdsd:sdfsdf34534DFGDFG32434", false, false);
        modUser.setGroups(user.getGroups());
        assertEquals(userGroupsDao.updateUser(modUser), 0);
        User readUser = userGroupsDao.findUserById(4L);
        assertNotNull(readUser);
        assertFalse(readUser.isIdentical(modUser));
        assertTrue(readUser.isIdentical(user));
    }

    @Test(dependsOnMethods = "testGetUsersWhichPasswordIsAboutToExpire")
    public void testDeleteUser() throws SQLException {
        assertEquals(userGroupsDao.deleteUser("u4"), 2);
        assertNull(userGroupsDao.findUserById(4L));
    }

    @Test(expectedExceptions = {IllegalStateException.class},
            expectedExceptionsMessageRegExp = ".*not initialized.*Groups missing.*")
    public void testCreateUserFailedNoGroups() throws SQLException {
        final User u6 = new User(6L, "u6", "6pass", null, "6@mail.com", false, false, false, null);
        userGroupsDao.createUser(u6);
    }

    public void testCreateUserWithGroups() throws SQLException {
        Group g70 = new Group(70L, "g70", "Test G70", false, null, null, false);
        final User u7 = new User(7L, "u7", "7pass", null, "7@mail.com", false, false, false, null);
        final HashSet<UserGroup> groups = new HashSet<UserGroup>(1);
        groups.add(new UserGroup(7L, 1L, null));
        groups.add(new UserGroup(7L, 2L, "ll"));
        groups.add(new UserGroup(7L, 70L, "dl"));
        u7.setGroups(groups);
        assertEquals(userGroupsDao.createGroup(g70), 1);
        int res = userGroupsDao.createUser(u7);
        assertEquals(res, 4);
        User user = userGroupsDao.findUserById(7L);
        assertNotNull(user);
        assertTrue(user.isIdentical(u7));
    }

    @Test(dependsOnMethods = "testDeleteUser")
    public void testDeleteUserAndGroupLinked() throws SQLException {
        User user = userGroupsDao.findUserById(7L);
        assertNotNull(user);
        assertEquals(user.getUserId(), 7L);
        assertEquals(user.getUsername(), "u7");
        assertEquals(user.getGroups().size(), 3);
        List<User> inGroup = userGroupsDao.findUsersInGroup(1L);
        assertEquals(inGroup.size(), 2);
        assertTrue(lookFor7(inGroup), "Did not find user 7 in " + Iterators.toString(inGroup.iterator()));
        Group group = userGroupsDao.findGroupById(70L);
        assertNotNull(group);
        assertEquals(group.getGroupId(), 70L);
        assertEquals(group.getGroupName(), "g70");
        boolean found70 = lookFor70InUserGroups(user);
        assertTrue(found70, "Did not find group link to 70 in " + user.getGroups().toString());
        assertEquals(userGroupsDao.deleteGroup("g70"), 2);
        assertNull(userGroupsDao.findGroupById(70L));
        assertNull(userGroupsDao.findGroupByName("g70"));

        User userNo70 = userGroupsDao.findUserById(7L);
        assertFalse(lookFor70InUserGroups(userNo70));
        assertEquals(userNo70.getGroups().size(), 2);

        assertEquals(userGroupsDao.deleteUser("u7"), 4);
        assertNull(userGroupsDao.findUserById(7L));
        assertNull(userGroupsDao.findUsernameByUserId(7L));
        assertEquals(userGroupsDao.findUserIdByUsername("u7"), 0L);


        inGroup = userGroupsDao.findUsersInGroup(1L);
        assertEquals(inGroup.size(), 1);
        assertFalse(lookFor7(inGroup), "Should not find user 7 in " + Iterators.toString(inGroup.iterator()));
    }

    private boolean lookFor7(List<User> inGroup) {
        boolean found7 = false;
        for (User u : inGroup) {
            if (u.getUserId() == 7L) {
                found7 = true;
                assertEquals(u.getUsername(), "u7");
            }
        }
        return found7;
    }

    private boolean lookFor70InUserGroups(User user) {
        boolean found70 = false;
        for (UserGroup userGroup : user.getGroups()) {
            if (userGroup.getGroupId() == 70L) {
                found70 = true;
                assertEquals(userGroup.getUserId(), 7L);
                assertEquals(userGroup.getRealm(), "dl");
            }
        }
        return found70;
    }

    @Test(dependsOnMethods = {"testDeleteUserAndGroupLinked", "testDeleteUser"})
    public void testAddUsersToGroup() throws SQLException {
        // User 5, 6 and 8 are left over from groups link failure => delete silently
        userGroupsDao.deleteUser("u5");
        userGroupsDao.deleteUser("u6");
        userGroupsDao.deleteUser("u8");
        checkAllUsernameMap(userGroupsDao.getAllUsernamePerIds());
        checkAllGroupNameMap(userGroupsDao.getAllGroupNamePerIds());
        checkUserCollection(userGroupsDao.getAllUsers(true), ImmutableSet.of(1, 2, 3, 10,11, 12, 15, 16));
        checkUserCollection(userGroupsDao.getAllUsers(false), ImmutableSet.of(1, 12,15));
        assertTrue(userGroupsDao.findUsersInGroup(3L).isEmpty());
        assertEquals(userGroupsDao.addUsersToGroup(3L, ImmutableList.of("u1", "u2", "u3"), "artifactory"), 3);
        checkUserCollection(userGroupsDao.findUsersInGroup(3L), ImmutableSet.of(1, 2, 3), 1);
        checkGroupInUser(userGroupsDao.findUserByName("u3"));
        assertEquals(userGroupsDao.removeUsersFromGroup(3L, ImmutableList.of("u1", "u2", "u3")), 3);
        assertTrue(userGroupsDao.findUsersInGroup(3L).isEmpty());
        checkUserCollection(userGroupsDao.getAllUsers(true), ImmutableSet.of(1, 2, 3, 10, 11, 12, 15, 16));
    }

    private void checkAllUsernameMap(Map<Long, String> allUsernamePerIds) {
        assertEquals(allUsernamePerIds.size(), 8);
        for (Map.Entry<Long, String> entry : allUsernamePerIds.entrySet()) {
            switch (entry.getKey().intValue()) {
                case 1:
                case 2:
                case 3:
                    assertEquals(entry.getValue(), "u"+entry.getKey());
                    break;
                case 10:
                    assertEquals(entry.getValue(), "adminByGroup");
                    break;
                case 11:
                    assertEquals(entry.getValue(), "adminWithAdminGroup");
                    break;
                case 12:
                    assertEquals(entry.getValue(), "userNoGroups");
                    break;
                case 15:
                    assertEquals(entry.getValue(), "anonymous");
                    break;
                case 16:
                    assertEquals(entry.getValue(), "admin");
                    break;
                default:
                    fail("User ID "+entry.getKey()+" unknown");
            }
        }
    }

    private void checkAllGroupNameMap(Map<Long, String> allGroupNamePerIds) {
        assertEquals(allGroupNamePerIds.size(), 5);
        for (Map.Entry<Long, String> entry : allGroupNamePerIds.entrySet()) {
            switch (entry.getKey().intValue()) {
                case 1:
                case 2:
                case 3:
                    assertEquals(entry.getValue(), "g"+entry.getKey());
                    break;
                case 15:
                    assertEquals(entry.getValue(), "readers");
                    break;
                case 16:
                    assertEquals(entry.getValue(), "admins");
                    break;
                default:
                    fail("User ID "+entry.getKey()+" unknown");
            }
        }
    }

    private void checkGroupInUser(User userById) {
        ImmutableSet<UserGroup> groups = userById.getGroups();
        for (UserGroup group : groups) {
            assertEquals(group.getGroupId(), 3L);
            assertEquals(group.getRealm(), "artifactory");
        }
    }

    @Test(expectedExceptions = {SQLException.class})
    public void testCreateUserWithFailedGroups() throws SQLException {
        // TORE: [by fsi] use the error handling to verify we broke the correct constraint
        final User u8 = new User(8L, "u8", "8pass", null, "8@mail.com", false, false, false, null);
        final HashSet<UserGroup> groups = new HashSet<UserGroup>(1);
        groups.add(new UserGroup(8L, 1L, null));
        // Group Id does not exists
        groups.add(new UserGroup(8L, 44L, "ll"));
        u8.setGroups(groups);
        userGroupsDao.createUser(u8);
    }

    @Test(expectedExceptions = {SQLException.class})
    public void testCreateGroupSameId() throws SQLException {
        // TORE: [by fsi] use the error handling to verify we broke the correct constraint
        userGroupsDao.createGroup(new Group(1L, "g6", "Test G4", false, "test realm", "test att", false));
    }

    @Test(expectedExceptions = {SQLException.class})
    public void testCreateGroupSameName() throws SQLException {
        // TORE: [by fsi] use the error handling to verify we broke the correct constraint
        userGroupsDao.createGroup(new Group(5L, "g1", "Test G4", false, "test realm", "test att", false));
    }

    @Test(expectedExceptions = {SQLException.class})
    public void testCreateUserSameId() throws SQLException {
        // TORE: [by fsi] use the error handling to verify we broke the correct constraint
        userGroupsDao.createUser(new User(1L, "u6", "1pass", null, "1@mail.com", false, false, false, null));
    }

    @Test(expectedExceptions = {SQLException.class})
    public void testCreateUserSameName() throws SQLException {
        // TORE: [by fsi] use the error handling to verify we broke the correct constraint
        userGroupsDao.createUser(new User(5L, "u1", "1pass", null, "1@mail.com", false, false, false, null));
    }

    @Test(expectedExceptions = {SQLException.class})
    public void testCreateUserPropNullKey() throws SQLException {
        // TORE: [by fsi] use the error handling to verify we broke the correct constraint
        userPropsDao.addUserPropertyById(2L, null, "F");
    }

    @Test(expectedExceptions = {SQLException.class})
    public void testCreateUserPropNoUser() throws SQLException {
        // TORE: [by fsi] use the error handling to verify we broke the correct constraint
        userPropsDao.addUserPropertyById(3456L, "test.fail", "F");
    }

    public void testFindUserById() throws SQLException {
        assertUser1(userGroupsDao.findUserById(1L), 0);
        assertUser2(userGroupsDao.findUserById(2L), 0);
        assertUser3(userGroupsDao.findUserById(3L), 0);
        assertNull(userGroupsDao.findUserById(30L));
    }

    public void testFindUserByName() throws SQLException {
        assertUser1(userGroupsDao.findUserByName("u1"), 0);
        assertUser2(userGroupsDao.findUserByName("u2"), 0);
        assertUser3(userGroupsDao.findUserByName("u3"), 0);
        assertNull(userGroupsDao.findUserByName("does not exists"));
    }

    public void testFindUserProperties() throws SQLException {
        assertEquals(userPropsDao.getUserProperty("u1", "test.null"), null);
        assertEquals(userPropsDao.getUserProperty("u1", "test.dup"), "A");
        assertEquals(userPropsDao.getUserProperty("u2", "test.dup"), "B");
        assertEquals(userPropsDao.getUserProperty("anonymous", "test.login"), "http://git/login");
        assertEquals(userPropsDao.getUserProperty("anonymous", "test.logout"), "http://git/logout");
    }

    public void testFindUserByProperty() throws SQLException {
        assertEquals(userPropsDao.getUserIdByProperty("test.dup", "A"), 1L);
        assertEquals(userPropsDao.getUserIdByProperty("test.dup", "B"), 2L);
        // TODO: [by fsi] support search for key only
        //assertEquals(userPropsDao.getUserIdByProperty("test.null", null), 1L);
        assertEquals(userPropsDao.getUserIdByProperty("test.fail", "W"), 0L);
        assertEquals(userPropsDao.getUserIdByProperty("test.null", "DD"), 0L);
    }

    public void createUserProperty() throws SQLException {
        // new prop
        assertTrue(userPropsDao.addUserPropertyById(2L, "test.new", "W"));
        // update and reset
        assertTrue(userPropsDao.addUserPropertyById(1L, "test.null", "W"));
        assertTrue(userPropsDao.addUserPropertyById(1L, "test.null", null));
    }

    public void testNoUserProperties() throws SQLException {
        assertEquals(userPropsDao.getUserProperty("u3", "test.fail"), null);
        assertEquals(userPropsDao.getUserProperty("u2", "test.fail"), null);
    }

    public void testFindGroupById() throws SQLException {
        assertGroup1(userGroupsDao.findGroupById(1L));
        assertGroup2(userGroupsDao.findGroupById(2L));
        assertNull(userGroupsDao.findGroupById(30L));
    }

    public void testFindGroupByName() throws SQLException {
        assertGroup1(userGroupsDao.findGroupByName("g1"));
        assertGroup2(userGroupsDao.findGroupByName("g2"));
        assertNull(userGroupsDao.findGroupByName("does not exists"));
    }

    @Test(dependsOnMethods = {"testDeleteGroup", "testDeleteUserAndGroupLinked"})
    public void testFindGroupByFilter() throws SQLException {
        checkGroupCollection(userGroupsDao.findGroups(UserGroupsDao.GroupFilter.ALL), ImmutableSet.of(1, 2, 3, 15, 16));
        checkGroupCollection(userGroupsDao.findGroups(UserGroupsDao.GroupFilter.DEFAULTS), ImmutableSet.of(2, 15));
        checkGroupCollection(userGroupsDao.findGroups(UserGroupsDao.GroupFilter.EXTERNAL), ImmutableSet.of(2));
        checkGroupCollection(userGroupsDao.findGroups(UserGroupsDao.GroupFilter.INTERNAL), ImmutableSet.of(1, 3, 15, 16));
    }

    @Test(dependsOnMethods = "testCreateUserWithNulls")
    public void testLockUser() throws SQLException {
        User user = userGroupsDao.findUserById(4L);
        assertNotNull(user);
        assertEquals(user.getUserId(), 4L);
        assertEquals(user.getUsername(), "u4");
        assertEquals(userGroupsDao.lockUser(user), 1);
        User readUser = userGroupsDao.findUserById(4L);
        assertNotNull(readUser);
        assertTrue(readUser.isLocked());
    }

    @Test(dependsOnMethods = "testLockUser")
    public void testListLockedUsers() throws SQLException {
        Set<String> lockedUsers = userGroupsDao.getLockedUsersNames();
        assertNotNull(lockedUsers);
        assertEquals(lockedUsers.size(), 1);
        assertEquals(lockedUsers.toArray(new String[0])[0], "u4");
    }

    @Test(dependsOnMethods = "testListLockedUsers")
    public void testUnLockUser() throws SQLException {
        User user = userGroupsDao.findUserById(4L);
        assertNotNull(user);
        assertEquals(user.getUserId(), 4L);
        assertEquals(user.getUsername(), "u4");
        assertTrue(userGroupsDao.unlockUser(user));
        User readUser = userGroupsDao.findUserById(4L);
        assertNotNull(readUser);
        assertFalse(readUser.isLocked());
    }

    @Test(dependsOnMethods = "testUnLockUser")
    public void testUnLockAllUsers() throws SQLException {
        // lock user
        User user = userGroupsDao.findUserById(4L);
        assertNotNull(user);
        assertEquals(user.getUserId(), 4L);
        assertEquals(user.getUsername(), "u4");
        assertEquals(userGroupsDao.lockUser(user), 1);
        User readUser = userGroupsDao.findUserById(4L);
        assertNotNull(readUser);
        assertTrue(readUser.isLocked());

        // unlock all users
        user = userGroupsDao.findUserById(4L);
        assertNotNull(user);
        assertEquals(user.getUserId(), 4L);
        assertEquals(user.getUsername(), "u4");
        assertEquals(userGroupsDao.unlockAllUsers(), 1);
        readUser = userGroupsDao.findUserById(4L);
        assertNotNull(readUser);
        assertFalse(readUser.isLocked());
    }

    @Test(dependsOnMethods = "testUnLockAllUsers")
    public void testExpirePassword() throws SQLException {
        User user = userGroupsDao.findUserById(4L);
        assertNotNull(user);
        assertEquals(user.getUserId(), 4L);
        assertEquals(user.getUsername(), "u4");

        userGroupsDao.expireUserPassword(user.getUsername());

        user = userGroupsDao.findUserById(4L);
        assertNotNull(user);
        assertTrue(user.isCredentialsExpired());
    }

    @Test(dependsOnMethods = "testExpirePassword")
    public void testRevalidatePassword() throws SQLException {
        User user = userGroupsDao.findUserById(4L);
        assertNotNull(user);
        assertEquals(user.getUserId(), 4L);
        assertEquals(user.getUsername(), "u4");

        userGroupsDao.unexpirePassword(user.getUsername());

        user = userGroupsDao.findUserById(4L);
        assertNotNull(user);
        assertFalse(user.isCredentialsExpired());
    }

    @Test(dependsOnMethods = "testRevalidatePassword")
    public void testExpirePasswordsForAllUsers() throws SQLException {
        User user = userGroupsDao.findUserById(4L);
        assertNotNull(user);
        assertEquals(user.getUserId(), 4L);
        assertEquals(user.getUsername(), "u4");

        userGroupsDao.expirePasswordForAllUsers();

        user = userGroupsDao.findUserById(4L);
        assertNotNull(user);
        assertTrue(user.isCredentialsExpired());
    }

    @Test(dependsOnMethods = "testExpirePasswordsForAllUsers")
    public void testRevalidatePasswordForAllUsers() throws SQLException {
        User user = userGroupsDao.findUserById(4L);
        assertNotNull(user);
        assertEquals(user.getUserId(), 4L);
        assertEquals(user.getUsername(), "u4");

        userGroupsDao.unexpirePasswordForAllUsers();

        user = userGroupsDao.findUserById(4L);
        assertNotNull(user);
        assertFalse(user.isCredentialsExpired());
    }

    @Test(dependsOnMethods = "testRevalidatePasswordForAllUsers")
    public void testChangePassword() throws SQLException {
        User user = userGroupsDao.findUserById(4L);
        assertNotNull(user);
        assertEquals(user.getUserId(), 4L);
        assertEquals(user.getUsername(), "u4");

        SaltedPassword newSaltedPassword =  new SaltedPassword("foo", "bar");
        userGroupsDao.changePassword(user.getUsername(), newSaltedPassword);

        user = userGroupsDao.findUserById(4L);
        assertNotNull(user);
        assertEquals(user.getPassword(), newSaltedPassword.getPassword());
        assertEquals(user.getSalt(), newSaltedPassword.getSalt());
    }

    @Test(dependsOnMethods = "testChangePassword")
    public void testMarkCredentialsExpired() throws SQLException {
        User user = userGroupsDao.findUserById(4L);
        assertNotNull(user);
        assertEquals(user.getUserId(), 4L);
        assertEquals(user.getUsername(), "u4");
        assertFalse(user.isCredentialsExpired());

        // since password was created today (along with user),
        // we pass artificial number (less than 1 day)
        userGroupsDao.markCredentialsExpired(new Long[]{4L});

        user = userGroupsDao.findUserById(4L);
        assertNotNull(user);
        assertTrue(user.isCredentialsExpired());
    }

    @Test(dependsOnMethods = "testMarkCredentialsExpired")
    public void testGetUsersWhichPasswordIsAboutToExpire() throws SQLException {
        userGroupsDao.unexpirePassword("u4");

        User user = userGroupsDao.findUserById(4L);
        assertNotNull(user);
        assertEquals(user.getUserId(), 4L);
        assertEquals(user.getUsername(), "u4");
        assertFalse(user.isCredentialsExpired());

        DateTime now = DateTime.now();
        final int millisInDay = 86400 * 1000;
        Set<PasswordExpiryUser> usersWhichPasswordIsAboutToExpire =
                userGroupsDao.getPasswordExpiredUsersByFilter(
                        (Long creationTime) -> shouldNotifyUser(creationTime, now.minusDays(1).getMillis(), millisInDay, 3 * millisInDay));
        assertEquals(usersWhichPasswordIsAboutToExpire.size(), 2);
        List<String> userNames = usersWhichPasswordIsAboutToExpire.stream()
                .map(PasswordExpiryUser::getUserName)
                .collect(Collectors.toList());
        assertTrue(userNames.contains("u4"));
        assertTrue(userNames.contains("u7"));
    }

    //Duplicated here to create same behavior as UserGroupService.getUsersWhichPasswordIsAboutToExpire()
    public boolean shouldNotifyUser (long creationTime, long millisNow, long millisDaysToKeepPassword,
            long millisDaysToStartNotificationsFrom) {
        return (millisNow >= ((creationTime + millisDaysToKeepPassword) - millisDaysToStartNotificationsFrom))
                && (millisNow < (creationTime + millisDaysToKeepPassword));
    }
}
