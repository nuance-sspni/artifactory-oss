package org.artifactory.common.config.wrappers;

import java.io.IOException;
import java.sql.SQLException;

/**
 * @author gidis
 */
public interface ConfigWrapper {

    void remove() throws SQLException;

    void create() throws IOException, SQLException;

    void modified() throws IOException, SQLException;

    void remoteRemove() throws IOException, SQLException;

    void remoteCreate() throws IOException, SQLException;

    void remoteModified() throws IOException, SQLException;

    String getName();
}
