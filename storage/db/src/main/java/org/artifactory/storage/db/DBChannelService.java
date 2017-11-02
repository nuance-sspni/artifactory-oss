package org.artifactory.storage.db;

import org.artifactory.common.config.db.DbChannel;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;

/**
 * @author gidis
 */
public interface DBChannelService extends DbChannel {
    @Transactional
    int executeUpdateInternal(String query, Object... params) throws SQLException;
}
