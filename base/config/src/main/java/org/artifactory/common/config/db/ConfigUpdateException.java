package org.artifactory.common.config.db;

import java.sql.SQLException;

/**
 * @author gidis
 */
public class ConfigUpdateException extends RuntimeException {

    public ConfigUpdateException(String message, SQLException se) {
        super(message, se);
    }
}
