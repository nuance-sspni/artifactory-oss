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

package org.artifactory.storage.db.itest;

import ch.qos.logback.classic.util.ContextInitializer;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.config.ConfigurationManager;
import org.artifactory.common.config.ConfigurationManagerImpl;
import org.artifactory.common.config.FileEventType;
import org.artifactory.common.config.broadcast.BroadcastChannel;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.config.db.ConfigsDataAccessObject;
import org.artifactory.common.config.db.DbChannel;
import org.artifactory.common.config.log.LogChannel;
import org.artifactory.storage.db.DbServiceImpl;
import org.artifactory.storage.db.spring.ArtifactoryTomcatDataSource;
import org.artifactory.storage.db.util.DbUtils;
import org.artifactory.storage.db.util.JdbcHelper;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.test.TestUtils;
import org.artifactory.util.CommonDbUtils;
import org.artifactory.util.ResourceUtils;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.sql.Connection;
import java.sql.SQLException;

import static org.artifactory.common.ArtifactoryHome.*;
import static org.artifactory.test.ChecksumTestUtils.randomHex;
import static org.testng.Assert.assertEquals;

/**
 * Base class for the low level database integration tests.
 *
 * @author Yossi Shaul
 */
//@TestExecutionListeners(TransactionalTestExecutionListener.class)
//@Transactional
//@TransactionConfiguration(defaultRollback = false)
@Test(groups = "dbtest")
@ContextConfiguration(locations = {"classpath:spring/db-test-context.xml"})
public abstract class DbBaseTest extends AbstractTestNGSpringContextTests {

    static {
        // use the itest logback config
        URL url = DbBaseTest.class.getClassLoader().getResource("logback-dbtest.xml");
        if (url == null) {
            throw new RuntimeException("Could not find logback-dbtest.xml");
        }
        System.setProperty(ContextInitializer.CONFIG_FILE_PROPERTY, url.getPath());
    }

    @Autowired
    protected JdbcHelper jdbcHelper;

    @Autowired
    protected DbServiceImpl dbService;

    @Autowired
    protected ArtifactoryDbProperties dbProperties;

    private ArtifactoryHomeBoundTest artifactoryHomeBoundTest;
    private DummyArtifactoryContext dummyArtifactoryContext;
    private ConfigurationManager configurationManager;

    @BeforeClass
    @Override
    protected void springTestContextPrepareTestInstance() throws Exception {
        artifactoryHomeBoundTest = createArtifactoryHomeTest();
        artifactoryHomeBoundTest.bindArtifactoryHome();
        configurationManager = new MockConfigurationManagerImpl(ArtifactoryHome.get());
        super.springTestContextPrepareTestInstance();
        System.out.println("Setting up test class " + this.getClass().getName() + " for DB of type: " + dbProperties.getDbType());

        dummyArtifactoryContext = new DummyArtifactoryContext(applicationContext);

        try (Connection connection = jdbcHelper.getDataSource().getConnection()) {
            DbTestUtils.refreshOrRecreateSchema(LoggerFactory.getLogger(getClass()), connection, dbProperties.getDbType());
        }
        TestUtils.invokeMethodNoArgs(dbService, "initializeIdGenerator");
    }

    @AfterClass
    public void verifyDbResourcesReleased() throws IOException, SQLException {
        // make sure there are no active connections
        /*PoolingDataSource ds = (PoolingDataSource) jdbcHelper.getDataSource();
        GenericObjectPool pool = TestUtils.getField(ds, "_pool", GenericObjectPool.class);
        assertEquals(pool.getNumActive(), 0, "Found " + pool.getNumActive() + " active connections after test ended:\n"
                + TestUtils.invokeMethodNoArgs(pool, "debugInfo"));*/
        ArtifactoryTomcatDataSource ds = (ArtifactoryTomcatDataSource) jdbcHelper.getDataSource();
        assertEquals(ds.getActiveConnectionsCount(), 0, "Found " + ds.getActiveConnectionsCount() +
                " active connections after test ended");
        artifactoryHomeBoundTest.unbindArtifactoryHome();
        // TODO: [by fsi] Derby DB cannot be shutdown in Suite since it uses the same DB for all tests
        //DbTestUtils.forceShutdownDerby(dbProperties.getProperty("db.home", null));
    }

    protected void addBean(Object bean, Class<?>... types) {
        dummyArtifactoryContext.addBean(bean, types);
    }

    protected ArtifactoryHomeBoundTest createArtifactoryHomeTest() throws IOException {
        return new ArtifactoryHomeBoundTest();
    }

    protected String randomSha1() {
        return randomHex(40);
    }

    @BeforeMethod
    public void bindArtifactoryHome() {
        artifactoryHomeBoundTest.bindArtifactoryHome();
    }

    @AfterMethod
    public void unbindArtifactoryHome() {
        artifactoryHomeBoundTest.unbindArtifactoryHome();
    }

    protected void importSql(String resourcePath) {
        InputStream resource = ResourceUtils.getResource(resourcePath);
        Connection con = null;
        try {
            con = jdbcHelper.getDataSource().getConnection();
            CommonDbUtils.executeSqlStream(con, resource);
            // update the id generator
            TestUtils.invokeMethodNoArgs(dbService, "initializeIdGenerator");
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            DbUtils.close(con);
        }
    }

    @BeforeMethod
    public void bindDummyContext() {
        ArtifactoryContextThreadBinder.bind(dummyArtifactoryContext);
    }

    @AfterMethod
    public void unbindDummyContext() {
        ArtifactoryContextThreadBinder.unbind();
    }

    private class MockConfigurationManagerImpl implements ConfigurationManager {
        public MockConfigurationManagerImpl(ArtifactoryHome home) {
            ConfigurationManagerImpl.initDbProperties(home);
            //  Ensure mimetypes file exist
            ensureConfigurationFileExist("/META-INF/default/" + MIME_TYPES_FILE_NAME, home.getMimeTypesFile());
            // Ensure artifactory system properties exit
            ensureConfigurationFileExist("/META-INF/default/" + ARTIFACTORY_SYSTEM_PROPERTIES_FILE, home.getArtifactorySystemPropertiesFile());
            // Ensure binarystore.xml exit
            ensureConfigurationFileExist("/META-INF/default/" + BINARY_STORE_FILE_NAME, home.getBinaryStoreXmlFile());
        }

        public void ensureConfigurationFileExist(String defaultContent, File file) {
            ConfigurationManagerImpl.ensureConfigurationFileExist(defaultContent, file);
        }

        @Override
        public LogChannel getLogChannel() {
            return null;
        }

        @Override
        public boolean isConfigTableExist() {
            return false;
        }

        @Override
        public ConfigsDataAccessObject getConfigsDao() {
            return null;
        }

        @Override
        public BroadcastChannel getBroadcastChannel() {
            return null;
        }

        @Override
        public void setPermanentDBChannel(DbChannel dbChannel) {

        }

        @Override
        public void setPermanentLogChannel() {

        }

        @Override
        public void setPermanentBroadcastChannel(BroadcastChannel propagationService) {

        }

        @Override
        public void remoteConfigChanged(String name, FileEventType eventType) throws Exception {

        }

        @Override
        public long getDeNormalizedTime(long timestamp) {
            return 0;
        }

        @Override
        public void initDbChannels() {

        }

        @Override
        public void startSync() {

        }

        @Override
        public void destroy() {

        }

        @Override
        public void initDbProperties() {

        }

        @Override
        public void fileChanged(File file, String configPrefix, WatchEvent.Kind<Path> eventType, long nanoTime) throws SQLException, IOException {

        }

        @Override
        public void forceFileChanged(File file, String contextPrefix, FileEventType eventType) throws SQLException, IOException {

        }

        @Override
        public long getNormalizedTime(long timestamp) {
            return 0;
        }
    }
}
