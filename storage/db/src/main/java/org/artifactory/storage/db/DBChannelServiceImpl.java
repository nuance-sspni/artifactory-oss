package org.artifactory.storage.db;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.config.db.DbType;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.util.JdbcHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author gidis
 */
@Service
public class DBChannelServiceImpl implements DBChannelService {

    @Autowired
    private JdbcHelper jdbcHelper;

    @Autowired
    private ArtifactoryDbProperties dbProperties;

    private ArtifactoryContext artifactoryContext;

    @PostConstruct
    private void init() throws Exception {
        artifactoryContext = ContextHelper.get();
    }

    @Override
    public ResultSet executeSelect(String query, Object... params) throws SQLException {
        return jdbcHelper.executeSelect(query, params);
    }

    @Override
    public int executeUpdate(String query, Object... params) throws SQLException {
        DBChannelService transactionalMe = artifactoryContext.beanForType(DBChannelService.class);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        ArtifactoryHome.bind(artifactoryContext.getArtifactoryHome());
        return transactionalMe.executeUpdateInternal(query, params);
    }

    @Override
    public DbType getDbType() {
        return dbProperties.getDbType();
    }

    @Override
    public void close() {
        // Nothing here the jdbc helper is closing itself
    }

    @Override
    public int executeUpdateInternal(String query, Object... params) throws SQLException {
        return jdbcHelper.executeUpdate(query, params);

    }
}
