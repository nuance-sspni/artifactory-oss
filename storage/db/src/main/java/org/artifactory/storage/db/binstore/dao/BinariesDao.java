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

package org.artifactory.storage.db.binstore.dao;

import com.google.common.collect.Lists;
import org.artifactory.api.storage.BinariesInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.common.ConstantValues;
import org.artifactory.storage.db.binstore.entity.BinaryEntity;
import org.artifactory.storage.db.util.BaseDao;
import org.artifactory.storage.db.util.DbUtils;
import org.artifactory.storage.db.util.JdbcHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

/**
 * A data access object for binaries table access.
 *
 * @author Yossi Shaul
 */
@Repository
public class BinariesDao extends BaseDao {
    public static final String TEMP_SHA1_PREFIX = "##";
    private static final Logger log = LoggerFactory.getLogger(BinariesDao.class);

    @Autowired
    public BinariesDao(JdbcHelper jdbcHelper) {
        super(jdbcHelper);
    }

    public boolean exists(String sha1) throws SQLException {
        int count = jdbcHelper.executeSelectCount("SELECT count(1) FROM binaries WHERE sha1 = ?", sha1);
        if (count > 1) {
            log.warn("Unexpected binaries count for checksum: '{}' - {}", sha1, count);
        }
        return count > 0;
    }

    @Nullable
    public BinaryEntity load(String sha1) throws SQLException {
        ResultSet resultSet = null;
        try {
            resultSet = jdbcHelper.executeSelect("SELECT sha1, md5, bin_length FROM binaries WHERE sha1 = ?", sha1);
            if (resultSet.next()) {
                return binaryFromResultSet(resultSet);
            }
        } finally {
            DbUtils.close(resultSet);
        }
        return null;
    }

    public Collection<BinaryEntity> findAll() throws SQLException {
        Collection<BinaryEntity> results = Lists.newArrayList();
        ResultSet resultSet = null;
        try {
            resultSet = jdbcHelper.executeSelect("SELECT sha1, md5, bin_length FROM binaries " +
                    " WHERE sha1 NOT LIKE '" + TEMP_SHA1_PREFIX + "%'");
            while (resultSet.next()) {
                results.add(binaryFromResultSet(resultSet));
            }
        } finally {
            DbUtils.close(resultSet);
        }
        return results;
    }

    public Collection<BinaryEntity> search(ChecksumType checksumType, Collection<String> validChecksums)
            throws SQLException {
        Collection<BinaryEntity> results = Lists.newArrayList();
        if (validChecksums == null || validChecksums.isEmpty()) {
            return results;
        }

        // Oracle limits the max elements in the IN clause to 1000. Lists bigger than max chunk value are done in
        // multiple queries
        List<String> checksums = Lists.newArrayList(validChecksums);
        final int CHUNK = ConstantValues.binaryProviderPruneChunkSize.getInt();
        // split to chunks of no more than CHUNK
        for (int i = 0; i < validChecksums.size(); i += CHUNK) {
            int chunkMaxIndex = Math.min(i + CHUNK, validChecksums.size());
            List<String> chunk = checksums.subList(i, chunkMaxIndex);
            ResultSet resultSet = null;
            try {
                resultSet = jdbcHelper.executeSelect("SELECT sha1, md5, bin_length FROM binaries" +
                        " WHERE " + checksumType.name() + " IN (#)", chunk);
                while (resultSet.next()) {
                    results.add(binaryFromResultSet(resultSet));
                }
            } finally {
                DbUtils.close(resultSet);
            }
        }

        return results;
    }

    public Collection<BinaryEntity> findPotentialDeletion() throws SQLException {
        Collection<BinaryEntity> results = Lists.newArrayList();
        ResultSet resultSet = null;
        try {
            resultSet = jdbcHelper.executeSelect("SELECT b.sha1, b.md5, b.bin_length FROM binaries b" +
                    " WHERE b.sha1 NOT LIKE '" + TEMP_SHA1_PREFIX + "%'" +
                    " AND NOT EXISTS (SELECT n.node_id FROM nodes n WHERE n.sha1_actual = b.sha1)" +
                    " ORDER BY b.bin_length DESC");
            while (resultSet.next()) {
                results.add(binaryFromResultSet(resultSet));
            }
        } finally {
            DbUtils.close(resultSet);
        }
        return results;
    }

    public int deleteEntry(String sha1ToDelete) throws SQLException {
        return jdbcHelper.executeUpdate("DELETE FROM binaries WHERE binaries.sha1 = ?" +
                " AND NOT EXISTS (SELECT n.node_id FROM nodes n WHERE n.sha1_actual = ?)"
                , sha1ToDelete, sha1ToDelete);
    }

    /**
     * @return A pair of long values where the first is the counts of the binaries table elements and the second is the
     * total binaries size.
     */
    public BinariesInfo getCountAndTotalSize() throws SQLException {
        ResultSet resultSet = null;
        try {
            resultSet = jdbcHelper.executeSelect("SELECT count(b.sha1), sum(b.bin_length) FROM binaries b" +
                    " WHERE b.sha1 NOT LIKE '" + TEMP_SHA1_PREFIX + "%'");
            resultSet.next();
            return new BinariesInfo(resultSet.getLong(1), resultSet.getLong(2));
        } finally {
            DbUtils.close(resultSet);
        }
    }

    public boolean create(BinaryEntity binaryEntity) throws SQLException {
        int updateCount = jdbcHelper.executeUpdate("INSERT INTO binaries (sha1, md5, bin_length) VALUES(?, ?, ?)",
                binaryEntity.getSha1(), binaryEntity.getMd5(), binaryEntity.getLength());
        return updateCount == 1;
    }

    private BinaryEntity binaryFromResultSet(ResultSet rs) throws SQLException {
        return new BinaryEntity(rs.getString(1), rs.getString(2), rs.getLong(3));
    }
}
