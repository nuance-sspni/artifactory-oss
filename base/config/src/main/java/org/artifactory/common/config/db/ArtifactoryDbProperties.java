package org.artifactory.common.config.db;

import org.apache.commons.lang.StringUtils;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.property.LinkedProperties;

import java.io.*;
import java.util.Iterator;
import java.util.Map;

/**
 * //TODO [by dan]: shay is about to do the db-infra changes, this rename is the first step
 * @author Gidi Shabat
 */
public class ArtifactoryDbProperties {

    public static final int DEFAULT_MAX_ACTIVE_CONNECTIONS = 100;
    public static final int DEFAULT_MAX_IDLE_CONNECTIONS = 10;
    private final LinkedProperties props;
    private final DbType dbType;
    private ArtifactoryHome home;
    private String dbHome;
    private File dbPropertiesFile;

    public ArtifactoryDbProperties(ArtifactoryHome home) {
        this(home, home.getDBPropertiesFile());
    }

    public ArtifactoryDbProperties(ArtifactoryHome home, File dbPropertiesFile) {
        this.dbPropertiesFile = dbPropertiesFile;
        if (!dbPropertiesFile.exists()) {
            throw new RuntimeException("Artifactory can't start without DB properties file! File not found at '" +
                    dbPropertiesFile.getAbsolutePath() + "'");
        }
        this.home = home;
        try {
            props = new LinkedProperties();
            try (FileInputStream pis = new FileInputStream(dbPropertiesFile)) {
                props.load(pis);
            }

            trimValues();
            assertMandatoryProperties();

            dbType = DbType.parse(getProperty(Key.type));

            // configure embedded derby
            if (dbType == DbType.DERBY) {
                System.setProperty("derby.stream.error.file", new File(home.getLogDir(), "derby.log").getAbsolutePath());
                String url = getConnectionUrl();
                dbHome = props.getProperty("db.home", home.getDataDir().getAbsolutePath() + "/derby");
                url = url.replace("{db.home}", dbHome);
                props.setProperty("db.home", dbHome);
                props.setProperty(Key.url.key, url);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load artifactory DB properties from '"
                    + dbPropertiesFile.getAbsolutePath() + "' due to :" + e.getMessage(), e);
        }
    }

    public File getDbPropertiesFile() {
        return dbPropertiesFile;
    }

    public String getUsername() {
        return props.getProperty(Key.username.key);
    }

    public String getConnectionUrl() {
        return props.getProperty(Key.url.key);
    }

    public String getPassword() {
        String password = getProperty(Key.password);
        password = CryptoHelper.decryptIfNeeded(home, password);
        return password;
    }

    public void setPassword(String updatedPassword) {
        props.setProperty(Key.password.key, updatedPassword);
    }

    public DbType getDbType() {
        return dbType;
    }

    private void trimValues() {
        Iterator<Map.Entry<String, String>> iter = props.iterator();
        while (iter.hasNext()) {
            Map.Entry<String, String> entry = iter.next();
            String value = entry.getValue();
            if (!StringUtils.trimToEmpty(value).equals(value)) {
                entry.setValue(StringUtils.trim(value));
            }
        }
    }

    private void assertMandatoryProperties() {
        Key[] mandatory = {Key.type, Key.url, Key.driver};
        for (Key mandatoryProperty : mandatory) {
            String value = getProperty(mandatoryProperty);
            if (StringUtils.isBlank(value)) {
                throw new IllegalStateException("Mandatory storage property '" + mandatoryProperty + "' doesn't exist");
            }
        }
    }

    public String getProperty(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    public String getProperty(Key property) {
        return props.getProperty(property.key);
    }

    public String getDriverClass() {
        return getProperty(Key.driver);
    }

    public boolean getBooleanProperty(String key, boolean defaultValue) {
        return Boolean.parseBoolean(getProperty(key, defaultValue + ""));
    }

    public int getIntProperty(String key, int defaultValue) {
        return Integer.parseInt(getProperty(key, defaultValue + ""));
    }

    public int getMaxActiveConnections() {
        return getIntProperty(Key.maxActiveConnections.key, DEFAULT_MAX_ACTIVE_CONNECTIONS);
    }

    public int getMaxIdleConnections() {
        return getIntProperty(Key.maxIdleConnections.key, DEFAULT_MAX_IDLE_CONNECTIONS);
    }

    public long getLongProperty(String key, int defaultValue) {
        return Long.parseLong(getProperty(key, defaultValue + ""));
    }

    public boolean isPostgres() {
        return dbType == DbType.POSTGRESQL;
    }

    /**
     * update storage properties file;
     */
    public void updateDbPropertiesFile(File updateStoragePropFile) throws IOException {
        if (props != null) {
            try (OutputStream outputStream = new FileOutputStream(updateStoragePropFile)) {
                props.store(outputStream, "");
            }
        }
    }

    public enum Key {
        username, password, type, url, driver,
        maxActiveConnections("pool.max.active", null),
        maxIdleConnections("pool.max.idle", null);

        private final String key;
        private final Object defaultValue;

        Key() {
            this.key = name();
            this.defaultValue = null;
        }

        Key(String key, Object defaultValue) {
            this.key = key;
            this.defaultValue = defaultValue;
        }

        public String key() {
            return key;
        }

        public Object getDefaultValue() {
            return defaultValue;
        }

    }
}

