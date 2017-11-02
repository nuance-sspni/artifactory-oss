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

package org.artifactory.storage.db.spring;

import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.util.JdbcHelper;
import org.artifactory.storage.db.util.querybuilder.*;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jndi.JndiObjectFactoryBean;
import org.springframework.util.StringUtils;

import javax.naming.NamingException;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * A Spring {@link org.springframework.context.annotation.Configuration} to initialized database beans.
 *
 * @author Yossi Shaul
 */
@Configuration
public class DbConfigFactory implements BeanFactoryAware {

    public static final String BEAN_PREFIX = "bean:";
    public static final String JNDI_PREFIX = "jndi:";
    private BeanFactory beanFactory;

    @Bean(name = "dataSource")
    public DataSource createDataSource() {
        ArtifactoryDbProperties dbProperties = beanFactory.getBean("dbProperties", ArtifactoryDbProperties.class);
        DataSource result = getDataSourceFromBeanOrJndi(dbProperties, "");
        if (result != null) {
            return result;
        } else {
            return new ArtifactoryTomcatDataSource(dbProperties);
        }
    }

    private DataSource getDataSourceFromBeanOrJndi(ArtifactoryDbProperties dbProperties, String suffix) {
        DataSource result = null;
        String connectionUrl = dbProperties.getConnectionUrl();
        if (StringUtils.startsWithIgnoreCase(connectionUrl, BEAN_PREFIX)) {
            result = beanFactory.getBean(connectionUrl.substring(BEAN_PREFIX.length()) + suffix, DataSource.class);
        } else if (StringUtils.startsWithIgnoreCase(connectionUrl, JNDI_PREFIX)) {
            String jndiName = connectionUrl.substring(JNDI_PREFIX.length());
            JndiObjectFactoryBean jndiObjectFactoryBean = new JndiObjectFactoryBean();
            jndiObjectFactoryBean.setJndiName(jndiName + suffix);
            try {
                jndiObjectFactoryBean.afterPropertiesSet();
            } catch (NamingException e) {
                throw new RuntimeException(e);
            }
            result = (DataSource) jndiObjectFactoryBean.getObject();
        }
        return result;
    }

    /**
     * Returns a separate non-transactional auto-commit datasource. This data source is currently used only by the id
     * generator.
     *
     * @return An auto-commit non-transactional datasource.
     */
    @Bean(name = "uniqueIdsDataSource")
    public DataSource createUniqueIdsDataSource() {
        ArtifactoryDbProperties dbProperties = beanFactory.getBean(ArtifactoryDbProperties.class);
        DataSource result = getDataSourceFromBeanOrJndi(dbProperties, "noTX");
        if (result != null) {
            return result;
        }

        return ArtifactoryTomcatDataSource.createUniqueIdDataSource(dbProperties);
    }

    @Bean(name = "dbProperties")
    public ArtifactoryDbProperties getDbProperties() throws IOException {
        ArtifactoryHome artifactoryHome = ContextHelper.get().getArtifactoryHome();
        ArtifactoryDbProperties dbProperties = artifactoryHome.getDBProperties();
        // first for loading of the driver class. automatic registration doesn't work on some Tomcat installations
        String driver = dbProperties.getDriverClass();
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load JDBC driver '" + driver + "'", e);
        }
        return dbProperties;
    }

//    @Bean(name = "storageProperties")
//    public StorageProperties getStorageProperties() throws IOException {
//        return new StorageProperties();
//    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    /**
     * create  a query builder instance per db type
     *
     * @return query builder instance
     */
    @Bean(name = "queryBuilder", autowire = Autowire.BY_TYPE)
    public IQueryBuilder createSqlBuilder() throws SQLException {
        JdbcHelper jdbcHelper = beanFactory.getBean(JdbcHelper.class);
        ArtifactoryDbProperties dbProperties = beanFactory.getBean(ArtifactoryDbProperties.class);
        String productName = dbProperties.getDbType().toString();
        Connection connection = jdbcHelper.getDataSource().getConnection();
        connection.close();
        IQueryBuilder queryBuilder;
        switch (productName) {
            case "oracle":
                queryBuilder = new OracleQueryBuilder();
                break;
            case "mssql":
                queryBuilder = new SqlServerQueryBuilder();
                break;
            case "derby":
                queryBuilder = new DerbyQueryBuilder();
                break;
            case "postgresql":
                queryBuilder = new PostgresqlQueryBuilder();
                break;
            case "mysql":
                queryBuilder = new MysqlQueryBuilder();
                break;
            default:
                queryBuilder = new DerbyQueryBuilder();
                break;
        }
        return queryBuilder;
    }
}
