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

package org.artifactory.storage.db.security.dao;

import com.google.common.base.Strings;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.artifactory.api.security.PasswordExpiryUser;
import org.artifactory.sapi.security.SecurityConstants;
import org.artifactory.security.SaltedPassword;
import org.artifactory.storage.db.security.entity.Group;
import org.artifactory.storage.db.security.entity.User;
import org.artifactory.storage.db.security.entity.UserGroup;
import org.artifactory.storage.db.util.BaseDao;
import org.artifactory.storage.db.util.DbUtils;
import org.artifactory.storage.db.util.JdbcHelper;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Predicate;

/**
 * Date: 8/26/12
 * Time: 11:08 PM
 *
 * @author freds
 */
@Repository
public class UserGroupsDao extends BaseDao {

    @Autowired
    private UserPropertiesDao userPropertiesDao;

    @Autowired
    public UserGroupsDao(JdbcHelper jdbcHelper) {
        super(jdbcHelper);
    }

    private static final String GET_ALL_USERNAMES_BASE_QUERY = "SELECT DISTINCT u.username " +
            "FROM users u " +
            "LEFT JOIN users_groups ug " +
            "ON (u.user_id = ug.user_id) " +
            "LEFT JOIN groups g " +
            "ON (g.group_id = ug.group_id) ";

    public int createGroup(Group group) throws SQLException {
        return jdbcHelper.executeUpdate("INSERT INTO groups VALUES(?, ?, ?, ?, ?, ?, ?)",
                group.getGroupId(), group.getGroupName(), group.getDescription(),
                booleanAsByte(group.isNewUserDefault()), group.getRealm(), group.getRealmAttributes(),  booleanAsByte(group.isAdminPrivileges()));
    }

    public int updateGroup(Group group) throws SQLException {
        return jdbcHelper.executeUpdate("UPDATE groups SET " +
                " description = ?, default_new_user = ?," +
                " realm = ?, realm_attributes = ?," +
                " admin_privileges = ?" +
                " WHERE group_id = ? AND group_name = ?",
                group.getDescription(), booleanAsByte(group.isNewUserDefault()),
                group.getRealm(), group.getRealmAttributes(), booleanAsByte(group.isAdminPrivileges()),
                group.getGroupId(), group.getGroupName());
    }

    public int deleteGroup(String groupName) throws SQLException {
        Group group = findGroupByName(groupName);
        if (group == null) {
            // Group doesn't exist
            return 0;
        }
        int res = jdbcHelper.executeUpdate("DELETE FROM users_groups WHERE group_id = ?", group.getGroupId());
        res += jdbcHelper.executeUpdate("DELETE FROM groups WHERE group_id = ?", group.getGroupId());
        return res;
    }

    public int createUser(User user) throws SQLException {
        int res = jdbcHelper.executeUpdate("INSERT INTO users VALUES(" +
                        " ?," +
                        " ?, ?, ?," +
                        " ?, ?," +
                        " ?, ?, ?," +
                        " ?, ?, ?," +
                        " ?, ?," +
                        " ?, ?," +
                        " ?, ?, ?)",
                user.getUserId(),
                user.getUsername(), nullIfEmpty(user.getPassword()), nullIfEmpty(user.getSalt()),
                nullIfEmpty(user.getEmail()), nullIfEmpty(user.getGenPasswordKey()),
                booleanAsByte(user.isAdmin()), booleanAsByte(user.isEnabled()),
                booleanAsByte(user.isUpdatableProfile()),
                nullIfEmpty(user.getRealm()), nullIfEmpty(user.getPrivateKey()), nullIfEmpty(user.getPublicKey()),
                user.getLastLoginTimeMillis(), nullIfEmpty(user.getLastLoginClientIp()),
                null, null,
                nullIfEmpty(user.getBintrayAuth()), booleanAsByte(user.isLocked()), booleanAsByte(user.isCredentialsExpired()));

        for (UserGroup userGroup : user.getGroups()) {
            res += jdbcHelper.executeUpdate("INSERT INTO users_groups VALUES (?, ?, ?)",
                    userGroup.getUserId(), userGroup.getGroupId(), userGroup.getRealm());
        }

        setPasswordCreatedNow(user.getUsername());

        return res;
    }

    public int updateUser(User user) throws SQLException {
        int res = jdbcHelper.executeUpdate("UPDATE users SET " +
                        " password = ?, salt = ?," +
                        " email = ?, gen_password_key = ?," +
                        " admin = ?, enabled = ?, updatable_profile = ?," +
                        " realm = ?, private_key = ?, public_key = ?," +
                        " last_login_time = ?, last_login_ip = ?," +
                        " bintray_auth = ?, credentials_expired = ?" +
                        " WHERE user_id = ? AND username = ?",
                nullIfEmpty(user.getPassword()), nullIfEmpty(user.getSalt()),
                nullIfEmpty(user.getEmail()), nullIfEmpty(user.getGenPasswordKey()),
                booleanAsByte(user.isAdmin()), booleanAsByte(user.isEnabled()),
                booleanAsByte(user.isUpdatableProfile()),
                nullIfEmpty(user.getRealm()), nullIfEmpty(user.getPrivateKey()), nullIfEmpty(user.getPublicKey()),
                user.getLastLoginTimeMillis(), nullIfEmpty(user.getLastLoginClientIp()),
                // TODO Cleanup
                /*THE lastAccessTimeMillis and lastAccessClientIP  NOT IN USE
                user.getLastAccessTimeMillis(), nullIfEmpty(user.getLastAccessClientIp()),*/
                nullIfEmpty(user.getBintrayAuth()), booleanAsByte(user.isCredentialsExpired()),
                user.getUserId(), user.getUsername());
        if (res == 1) {
            jdbcHelper.executeUpdate("DELETE FROM users_groups WHERE user_id = ?", user.getUserId());
            for (UserGroup userGroup : user.getGroups()) {
                res += jdbcHelper.executeUpdate("INSERT INTO users_groups VALUES (?, ?, ?)",
                        userGroup.getUserId(), userGroup.getGroupId(), userGroup.getRealm());
            }
        }
        return res;
    }

    public int deleteUser(String username) throws SQLException {
        long userId = findUserIdByUsername(username);
        if (userId == 0L) {
            // User already deleted
            return 0;
        }
        int res = jdbcHelper.executeUpdate("DELETE FROM users_groups WHERE user_id = ?", userId);
        res += jdbcHelper.executeUpdate("DELETE FROM user_props WHERE user_id = ?", userId);
        res += jdbcHelper.executeUpdate("DELETE FROM users WHERE user_id = ?", userId);
        return res;
    }

    public long findUserIdByUsername(String username) throws SQLException {
        ResultSet resultSet = null;
        try {
            resultSet = jdbcHelper.executeSelect("SELECT user_id FROM users WHERE username = ?", username);
            if (resultSet.next()) {
                return resultSet.getLong(1);
            }
            return 0L;
        } finally {
            DbUtils.close(resultSet);
        }
    }

    public String findUsernameByUserId(long userId) throws SQLException {
        ResultSet resultSet = null;
        try {
            resultSet = jdbcHelper.executeSelect("SELECT username FROM users WHERE user_id = ?", userId);
            if (resultSet.next()) {
                return resultSet.getString(1);
            }
            return null;
        } finally {
            DbUtils.close(resultSet);
        }
    }

    public Map<Long, String> getAllUsernamePerIds() throws SQLException {
        Map<Long, String> results = new HashMap<>();
        String query = "SELECT user_id, username FROM users";
        try (ResultSet resultSet = jdbcHelper.executeSelect(query);){
            while (resultSet.next()) {
                results.put(resultSet.getLong(1), resultSet.getString(2));
            }
        }
        return results;
    }

    public Map<Long, String> getAllGroupNamePerIds() throws SQLException {
        Map<Long, String> results = new HashMap<>();
        String query = "SELECT group_id, group_name FROM groups";
        try (ResultSet resultSet = jdbcHelper.executeSelect(query)){
            while (resultSet.next()) {
                results.put(resultSet.getLong(1), resultSet.getString(2));
            }
        }
        return results;
    }

    public Map<String, Long> getAllGroupIdsToNames() throws SQLException {
        Map<String, Long> results = new HashMap<>();
        String query = "SELECT group_id, group_name FROM groups";
        try(ResultSet resultSet = jdbcHelper.executeSelect(query)) {
            while (resultSet.next()) {
                results.put(resultSet.getString(2), resultSet.getLong(1));
            }
        }
        return results;
    }

    /**
     * Get all user names and if they have admin/group admin permissions
     */
    public List<String> getAllAdminUserNames() throws SQLException {
        return getAllUserNames("WHERE u.admin = 1 OR g.admin_privileges = 1");
    }

    /**
     * Get all user names and if they don't have admin/group admin permissions
     */
    public List<String> getAllNonAdminUserNames() throws SQLException {
        return getAllUserNames("WHERE u.admin = 0 AND (g.admin_privileges = 0 OR g.admin_privileges IS NULL)");
    }

    private List<String> getAllUserNames(String whereClause) throws SQLException {
        List<String> results = Lists.newArrayList();
        try (ResultSet resultSet = jdbcHelper.executeSelect(GET_ALL_USERNAMES_BASE_QUERY + whereClause)){
            while (resultSet.next()) {
                results.add(resultSet.getString(1));
            }
        }
        return results;
    }

    public Collection<User> getAllUsers(boolean includeAdmins) throws SQLException {
        Map<Long, User> results = new HashMap<>();
        Map<Long, Set<UserGroup>> groups = new HashMap<>();
        String query = getSelectAllUsersQuery(includeAdmins);
        try (ResultSet resultSet = jdbcHelper.executeSelect(query)){
            while (resultSet.next()) {
                User user = userFromResultSet(resultSet);
                results.put(user.getUserId(), user);
                groups.put(user.getUserId(), new HashSet<>());
            }
        }
        try (ResultSet resultSet = jdbcHelper.executeSelect("SELECT * FROM users_groups")){
            while (resultSet.next()) {
                UserGroup userGroup = userGroupFromResultSet(resultSet);
                Set<UserGroup> userGroups = groups.get(userGroup.getUserId());
                // Group not found due to admin filtering
                if (userGroups != null) {
                    userGroups.add(userGroup);
                }
            }
        }
        for (Map.Entry<Long, Set<UserGroup>> entry : groups.entrySet()) {
            User user = results.get(entry.getKey());
            if (user == null) {
                throw new IllegalStateException("Map population of users and groups failed!");
            } else {
                user.setGroups(entry.getValue());
            }
        }
        return results.values();
    }

    private String getSelectAllUsersQuery(boolean includeAdmins) {
        if (!includeAdmins) {
            return  "SELECT DISTINCT u.* " +
                    "FROM users u " +
                    "LEFT JOIN users_groups ug " +
                    "ON (u.user_id = ug.user_id) " +
                    "LEFT JOIN groups g " +
                    "ON (ug.group_id = g.group_id) " +
                    "WHERE u.admin = 0 AND (g.admin_privileges = 0 OR g.admin_privileges IS NULL)";
        } else {
            return "SELECT * FROM users";
        }
    }

    public User findUserById(long userId) throws SQLException {
        ResultSet resultSet = null;
        User user = null;
        try {
            resultSet = jdbcHelper.executeSelect("SELECT * FROM users WHERE user_id = ?", userId);
            if (resultSet.next()) {
                user = userFromResultSet(resultSet);
            }
        } finally {
            DbUtils.close(resultSet);
        }
        if (user != null) {
            user.setGroups(findUserGroupByUserId(userId));
        }
        return user;
    }

    /**
     * Searches user in DB
     *
     * @param username user to look for
     * @return {@link User}
     *
     * @throws SQLException in case of lookup failure
     */
    public User findUserByName(String username) throws SQLException {
        return findUserByName(username, true);
    }

    /**
     * Searches user in DB
     *
     * @param username user to look for
     * @param includeGroups if user groups to be included
     * @return {@link User}
     *
     * @throws SQLException in case of lookup failure
     */
    public User findUserByName(String username, boolean includeGroups) throws SQLException {
        ResultSet resultSet = null;
        User user = null;
        try {
            resultSet = jdbcHelper.executeSelect("SELECT * FROM users WHERE username = ?", username);
            if (resultSet.next()) {
                user = userFromResultSet(resultSet);
            }
        } finally {
            DbUtils.close(resultSet);
        }
        if (user != null && includeGroups) {
            user.setGroups(findUserGroupByUserId(user.getUserId()));
        }
        return user;
    }

    public Group findGroupById(long groupId) throws SQLException {
        ResultSet resultSet = null;
        try {
            resultSet = jdbcHelper.executeSelect("SELECT * FROM groups WHERE group_id = ?", groupId);
            if (resultSet.next()) {
                return groupFromResultSet(resultSet);
            }
            return null;
        } finally {
            DbUtils.close(resultSet);
        }
    }

    /**
     * Fond the group entity by name if found.
     * Returns null if no group with this name found.
     *
     * @param groupName The name of the group to find
     * @return The Group DB entity object
     * @throws SQLException If the query cannot be executed
     */
    @Nullable
    public Group findGroupByName(String groupName) throws SQLException {
        ResultSet resultSet = null;
        try {
            resultSet = jdbcHelper.executeSelect("SELECT * FROM groups WHERE group_name = ?", groupName);
            if (resultSet.next()) {
                return groupFromResultSet(resultSet);
            }
            return null;
        } finally {
            DbUtils.close(resultSet);
        }
    }

    public int addUsersToGroup(long groupId, Collection<String> usernames, String realm) throws SQLException {
        if (usernames == null || usernames.isEmpty()) {
            throw new IllegalArgumentException("List of usernames to add group " + groupId + " to cannot be empty!");
        }
        // Find if the users passed already have the group
        Collection<String> toAddUsernames = usernames;
        ResultSet resultSet = null;
        try {
            resultSet = jdbcHelper.executeSelect("SELECT u.username" +
                    " FROM users u, users_groups ug" +
                    " WHERE ug.group_id = ?" +
                    " AND ug.user_id = u.user_id" +
                    " AND u.username IN (#)", groupId, usernames);
            if (resultSet.next()) {
                // Found some usernames that needs to be removed
                toAddUsernames = new HashSet<>(usernames);
                toAddUsernames.remove(resultSet.getString(1));
                while (resultSet.next()) {
                    toAddUsernames.remove(resultSet.getString(1));
                }
            }
        } finally {
            DbUtils.close(resultSet);
        }

        if (!toAddUsernames.isEmpty()) {
            return jdbcHelper.executeUpdate("INSERT INTO users_groups (user_id, group_id, realm)" +
                    " SELECT u.user_id, ?, ? FROM users u WHERE u.username IN (#)", groupId, realm, toAddUsernames);
        }
        return 0;
    }

    public int removeUsersFromGroup(long groupId, List<String> usernames) throws SQLException {
        return jdbcHelper.executeUpdate("DELETE FROM users_groups " +
                "WHERE group_id = ? " +
                "AND user_id IN (SELECT u.user_id FROM users u WHERE username IN (#))", groupId, usernames);
    }

    public Collection<Group> findGroups(GroupFilter filter) throws SQLException {
        List<Group> results = new ArrayList<>();
        ResultSet resultSet = null;
        try {
            resultSet = jdbcHelper.executeSelect("SELECT * FROM groups" + filter.filter);
            while (resultSet.next()) {
                results.add(groupFromResultSet(resultSet));
            }
            return results;
        } finally {
            DbUtils.close(resultSet);
        }
    }

    private Set<UserGroup> findUserGroupByUserId(long userId) throws SQLException {
        final Set<UserGroup> result = new HashSet<>(1);
        ResultSet resultSet = null;
        try {
            resultSet = jdbcHelper.executeSelect("SELECT * FROM users_groups WHERE user_id = ?", userId);
            while (resultSet.next()) {
                result.add(userGroupFromResultSet(resultSet));
            }
            return result;
        } finally {
            DbUtils.close(resultSet);
        }
    }

    public List<User> findUsersInGroup(long groupId) throws SQLException {
        List<User> results = new ArrayList<>();
        Set<UserGroup> userGroups = findUserGroupByGroupId(groupId);
        for (UserGroup userGroup : userGroups) {
            results.add(findUserById(userGroup.getUserId()));
        }
        return results;
    }

    public boolean adminUserExists() throws SQLException {
        ResultSet resultSet = null;
        try {
            resultSet = jdbcHelper.executeSelect("SELECT COUNT(user_id) FROM users WHERE admin = 1");
            if (resultSet.next()) {
                return resultSet.getLong(1) > 0L;
            }
            return false;
        } finally {
            DbUtils.close(resultSet);
        }
    }

    public int deleteAllGroupsAndUsers() throws SQLException {
        int res = jdbcHelper.executeUpdate("DELETE FROM users_groups");
        res += jdbcHelper.executeUpdate("DELETE FROM groups");
        res += jdbcHelper.executeUpdate("DELETE FROM user_props");
        res += jdbcHelper.executeUpdate("DELETE FROM users");
        return res;
    }

    private Set<UserGroup> findUserGroupByGroupId(long groupId) throws SQLException {
        Set<UserGroup> result = new HashSet<>(1);
        ResultSet resultSet = null;
        try {
            resultSet = jdbcHelper.executeSelect("SELECT * FROM users_groups WHERE group_id = ?", groupId);
            while (resultSet.next()) {
                result.add(userGroupFromResultSet(resultSet));
            }
            return result;
        } finally {
            DbUtils.close(resultSet);
        }
    }

    private User userFromResultSet(ResultSet rs) throws SQLException {
        String userName = rs.getString(2);
        return new User(rs.getLong(1), userName, emptyIfNull(rs.getString(3)),
                nullIfEmpty(rs.getString(4)), nullIfEmpty(rs.getString(5)), nullIfEmpty(rs.getString(6)),
                rs.getBoolean(7), rs.getBoolean(8), rs.getBoolean(9),
                nullIfEmpty(rs.getString(10)), nullIfEmpty(rs.getString(11)), nullIfEmpty(rs.getString(12)),
                rs.getLong(13), nullIfEmpty(rs.getString(14)),
                // TODO Cleanup
                /*rs.getLong(15), nullIfEmpty(rs.getString(16)) THE lastAccessTimeMillis and lastAccessClientIP  NOT IN USE*/
                nullIfEmpty(rs.getString(17)), rs.getBoolean(18), rs.getBoolean(19)
        );
    }

    private Group groupFromResultSet(ResultSet rs) throws SQLException {
        return new Group(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4),
                rs.getString(5), rs.getString(6), rs.getBoolean(7));
    }

    private UserGroup userGroupFromResultSet(ResultSet rs) throws SQLException {
        return new UserGroup(rs.getLong(1), rs.getLong(2), rs.getString(3));
    }

    public List<String> findAllUserNamesInGroup(long groupId) throws SQLException {
        List<String> userNames = Lists.newArrayList();
        String query = "SELECT u.username " +
                "FROM users u " +
                "INNER JOIN users_groups ug " +
                "ON (u.user_id = ug.user_id) " +
                "WHERE ug.group_id = ?";
        try (ResultSet rs = jdbcHelper.executeSelect(query, groupId)){
            while (rs.next()) {
                userNames.add(rs.getString(1));
            }
        }
        return userNames;
    }

    public List<String> findAllAdminGroups() throws SQLException {
        List<String> groupname = Lists.newArrayList();
        String query = "SELECT group_name " +
                "FROM groups " +
                "WHERE admin_privileges = 1";
        try (ResultSet rs = jdbcHelper.executeSelect(query)){
            while (rs.next()) {
                groupname.add(rs.getString(1));
            }
        }
        return groupname;
    }

    public Multimap<Long, Long> getAllUsersInGroups() throws SQLException {
        Multimap<Long, Long> usersInGroups = LinkedListMultimap.create();
        String query = "SELECT user_id, group_id FROM users_groups";
        try (ResultSet rs = jdbcHelper.executeSelect(query)){
            while (rs.next()) {
                usersInGroups.put(rs.getLong(2), rs.getLong(1));
            }
        }
        return usersInGroups;
    }

    public static enum GroupFilter {
        ALL(""),
        EXCLUDE_ADMINS(" WHERE admin_privileges IS NULL OR admin_privileges = 0"),
        DEFAULTS(" WHERE default_new_user=1"),
        EXTERNAL(" WHERE realm IS NOT NULL AND realm != '" + SecurityConstants.DEFAULT_REALM + "'"),
        INTERNAL(" WHERE realm IS NULL OR realm = '" + SecurityConstants.DEFAULT_REALM + "'");

        final String filter;

        GroupFilter(String filter) {
            this.filter = filter;
        }
    }

    /**
     * Locks user on login failure
     *
     * @param user
     * @return result
     * @throws SQLException
     */
    public int lockUser(User user) throws SQLException {
        return jdbcHelper.executeUpdate("UPDATE users SET locked = ? " +
                                              "WHERE user_id = ? AND username = ?",
                1, user.getUserId(), user.getUsername());
    }

    /**
     * Makes user password expired
     *
     * @param userName
     * @return result
     * @throws SQLException
     */
    public int expireUserPassword(String userName) throws SQLException {
        return setUserPasswordExpired(userName, true);
    }

    /**
     * Unexpires user's password
     *
     * @param userName
     * @return result
     * @throws SQLException
     */
    public int unexpirePassword(String userName) throws SQLException {
        int result1 = setUserPasswordExpired(userName, false);
        boolean result2 = setPasswordCreatedNow(userName);
        return !result2 ? -1 : result1;
    }

    /**
     * Sets password creation time to NOW
     *
     * @param userName
     * @return boolean
     *
     * @throws SQLException
     */
    private boolean setPasswordCreatedNow(String userName) throws SQLException {
        return userPropertiesDao.addUserPropertyByUserName(
                userName, "passwordCreated", Long.toString(DateTime.now().getMillis())
        );
    }

    /**
     * Sets credentials_expired value
     *
     * @param userName
     * @param passwordExpired
     *
     * @return result
     * @throws SQLException
     */
    private int setUserPasswordExpired(String userName, boolean passwordExpired) throws SQLException {
        return jdbcHelper.executeUpdate("UPDATE users SET credentials_expired = ?" +
                                               " WHERE username = ?",
                passwordExpired ? 1 : 0, userName);
    }

    /**
     * Unlocks user after it has been locked out
     *
     * @param user
     * @return result
     * @throws SQLException
     */
    public boolean unlockUser(User user) throws SQLException {
        int unlockResult = jdbcHelper.executeUpdate("UPDATE users SET locked = ? " +
                                                           "WHERE user_id = ? AND username = ?",
                0, user.getUserId(), user.getUsername()
        );
        return unlockResult == 1;
    }

    /**
     * Unlocks all locked out users
     *
     * @return result
     * @throws SQLException
     */
    public int unlockAdminUsers() throws SQLException {
        return jdbcHelper.executeUpdate("UPDATE users SET locked = ? " +
                                              "WHERE locked = ? and admin = ?",
                0, 1, 1);
    }

    /**
     * Unlocks all locked out users
     *
     * @return result
     * @throws SQLException
     */
    public int unlockAllUsers() throws SQLException {
        return jdbcHelper.executeUpdate("UPDATE users SET locked = ? " +
                                              "WHERE locked = ?",
               0, 1);
    }

    /**
     * Sets whether password is expired for all users
     *
     * @return result
     * @throws SQLException
     */
    private int setPasswordExpiredForAllUsers(boolean passwordExpired) throws SQLException {
        return jdbcHelper.executeUpdate("UPDATE users SET credentials_expired = ? " +
                                              "WHERE password is not NULL and username != 'anonymous'",
                passwordExpired ? 1 : 0
        );
    }

    /**
     * Expires password for all users
     *
     * @return result
     * @throws SQLException
     */
    public int expirePasswordForAllUsers() throws SQLException {
        return setPasswordExpiredForAllUsers(true);
    }

    /**
     * Unexpires password for all users
     *
     * @return result
     * @throws SQLException
     */
    public int unexpirePasswordForAllUsers() throws SQLException {
        int result1 = setPasswordExpiredForAllUsers(false);
        boolean result2 = userPropertiesDao.resetPasswordCreatedForAllUsers(Long.toString(new DateTime().getMillis()));
        return !result2 ? -1 : result1;
    }

    /**
     * @return locked out users (usernames)
     */
    public Set <String> getLockedUsersNames() throws SQLException {
        ResultSet resultSet = null;
        Set <String> users = Sets.newHashSet();
        try {
            String query = "SELECT username FROM users where locked = 1";

            resultSet = jdbcHelper.executeSelect(query);
            if (resultSet!=null)
                while (resultSet.next()) {
                    users.add(resultSet.getString(1));
                }
        } finally {
            DbUtils.close(resultSet);
        }
        return users;
    }

    /**
     * Changes user's password
     *
     * @param userName
     * @param saltedPassword
     *
     * @return result
     * @throws SQLException
     */
    public int changePassword(String userName, SaltedPassword saltedPassword) throws SQLException {
        return jdbcHelper.executeUpdate("UPDATE users SET password = ?, salt = ? " +
                                              "WHERE username = ?",
                saltedPassword.getPassword(),
                saltedPassword.getSalt(),
                userName
        );
    }

    /**
     * Retrieves users that are valid for password expiry checks (not anon, has internal password and password
     * creation time) based on the given {@param filter}
     *
     * @param filter        Predicate to filter users by
     * @return              'Lean' user models with needed data
     * @throws SQLException
     */
    public Set<PasswordExpiryUser> getPasswordExpiredUsersByFilter(Predicate<Long> filter) throws SQLException {
        ResultSet rs = null;
        Set<PasswordExpiryUser> results = Sets.newHashSet();
        String usersWithPassCreation =
                "SELECT u.user_id, u.username, u.email, d.prop_value " +
                        "FROM users u INNER JOIN user_props d ON (u.user_id = d.user_id)" +
                        "WHERE u.user_id = d.user_id " +
                        "and u.username != 'anonymous' " +
                        "and u.credentials_expired != 1 " +
                        "and u.password is NOT NULL " +
                        "and d.prop_key = 'passwordCreated' " +
                        "and d.prop_value is not NULL ";
        try {
            rs = jdbcHelper.executeSelect(usersWithPassCreation);
            while (rs.next()) {
                if(filter.test(rs.getLong(4))) {
                    results.add(new PasswordExpiryUser(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4)));
                }
            }
            return results;
        } finally {
            DbUtils.close(rs);
        }
    }

    /**
     * Marks user.credentials_expired=1 where password has expired
     *
     * @param usersBatch    Batch of {@link PasswordExpiryUser} to run update on
     * @throws SQLException
     */
    public void markCredentialsExpired(Long[] usersBatch) throws SQLException {
        String expireCredsQuery = "UPDATE users set credentials_expired = 1 where user_id = ?";
        //Start inserting or to the query from the second user id
        for (int i = 1; i < usersBatch.length; i++) {
            expireCredsQuery += " or user_id = ?";
        }
        jdbcHelper.executeUpdate(expireCredsQuery, usersBatch);
    }

    /**
     * @param userName
     * @return the date when last password was created
     * @throws SQLException
     */
    public Long getUserPasswordCreationTime(String userName) throws SQLException {
        String passwordCreated = userPropertiesDao.getUserProperty(userName, "passwordCreated");
        if (!Strings.isNullOrEmpty(passwordCreated)) {
            return Long.valueOf(passwordCreated);
        }
        return null;
    }
}
