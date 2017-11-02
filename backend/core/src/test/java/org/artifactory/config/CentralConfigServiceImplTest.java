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

package org.artifactory.config;

import com.google.common.collect.Maps;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.CoreAddonsImpl;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.property.PredefinedValue;
import org.artifactory.descriptor.property.Property;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.storage.db.fs.service.ConfigsServiceImpl;
import org.artifactory.storage.db.security.service.VersioningCache;
import org.artifactory.test.ArtifactoryHomeStub;
import org.artifactory.version.ArtifactoryVersion;
import org.artifactory.version.CompoundVersionDetails;
import org.joda.time.format.DateTimeFormat;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Map;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for the CentralConfigServiceImpl.
 *
 * @author Yossi Shaul
 */
@Test
public class CentralConfigServiceImplTest {

    private CentralConfigServiceImpl centralConfigService;
    private static CompoundVersionDetails earlyVersion = new CompoundVersionDetails(ArtifactoryVersion.v500, null, null, 0);
    private static CompoundVersionDetails recentVersion = new CompoundVersionDetails(ArtifactoryVersion.v522m001, null, null, 0);

    @BeforeMethod
    public void setup() {
        centralConfigService = new CentralConfigServiceImpl();
        ArtifactoryHome.bind(new ArtifactoryHomeStub());
    }


    public void idRefsUseTheSameObject() throws Exception {
        MutableCentralConfigDescriptor cc = getConfigDescriptor();
        // set and duplicate the descriptor
        CentralConfigServiceImpl configService = new CentralConfigServiceImpl();
        initCache(configService, cc);
        MutableCentralConfigDescriptor copy = configService.getMutableDescriptor();

        // make sure proxy object was not duplicated
        ProxyDescriptor proxy = copy.getProxies().get(0);
        ProxyDescriptor httpProxy = ((HttpRepoDescriptor) copy.getRemoteRepositoriesMap().get("http")).getProxy();
        assertTrue(proxy == httpProxy, "Proxy object was duplicated!");

        // make sure the property set was not duplicated
        PropertySet propSetCopy = copy.getPropertySets().get(0);
        LocalRepoDescriptor local1Copy = copy.getLocalRepositoriesMap().get("local1");
        PropertySet propSetCopyFromRepo = local1Copy.getPropertySet("propSet1");
        assertTrue(propSetCopy == propSetCopyFromRepo, "Proxy set object was duplicated!");
    }

    @Test
    public void keyStorePasswordMovesToDescriptor() throws Exception {
        String PASSWORD_CONFIG_KEY = "keystore:password";
        String PASSWORD = "123456";

        // Create mocks
        ConfigsServiceImpl configsService = createMock(ConfigsServiceImpl.class);
        ReflectionTestUtils.setField(centralConfigService, "configsService", configsService);
        expect(configsService.getConfig(PASSWORD_CONFIG_KEY)).andReturn(PASSWORD).once();
        expect(configsService.addOrUpdateConfig(eq(ArtifactoryHome.ARTIFACTORY_CONFIG_FILE), anyObject(), anyLong())).andReturn(false).once();
        configsService.deleteConfig(PASSWORD_CONFIG_KEY);
        expectLastCall().once();
        replay(configsService);

        AddonsManager addonsManager = createMock(AddonsManager.class);
        ReflectionTestUtils.setField(centralConfigService, "addonsManager", addonsManager);
        expect(addonsManager.addonByType(anyObject())).andReturn(new CoreAddonsImpl()).anyTimes();
        replay(addonsManager);

        ConfigurationChangesInterceptors interceptors = createMock(ConfigurationChangesInterceptors.class);
        ReflectionTestUtils.setField(centralConfigService, "interceptors", interceptors);
        interceptors.onBeforeSave(anyObject());
        expectLastCall().anyTimes();
        replay(interceptors);

        // Create descriptor
        MutableCentralConfigDescriptor configDescriptor = getConfigDescriptor();
        configDescriptor.setSecurity(new SecurityDescriptor());
        initCache(centralConfigService, configDescriptor);


        centralConfigService.moveKeyStorePasswordToConfig(earlyVersion, recentVersion, configDescriptor);

        assertTrue(PASSWORD.equals(configDescriptor.getSecurity().getSigningKeysSettings().getKeyStorePassword()));
        verify(configsService);
    }

    private void initCache(CentralConfigServiceImpl centralConfigService, MutableCentralConfigDescriptor configDescriptor) {
        VersioningCache<CentralConfigServiceImpl.CentralConfigDescriptorCache> cache = new VersioningCache<>(3000,
                () -> new CentralConfigServiceImpl.CentralConfigDescriptorCache(configDescriptor,configDescriptor.getServerName(),
                        DateTimeFormat.forPattern(configDescriptor.getDateFormat())));
        ReflectionTestUtils.setField(centralConfigService, "descriptorCache",cache);
    }

    private MutableCentralConfigDescriptor getConfigDescriptor() {
        MutableCentralConfigDescriptor cc = new CentralConfigDescriptorImpl();
        cc.setServerName("mymy");
        cc.setDateFormat("dd-MM-yy HH:mm:ss z");

        LocalRepoDescriptor local1 = new LocalRepoDescriptor();
        local1.setKey("local1");
        Map<String, LocalRepoDescriptor> localReposMap = Maps.newLinkedHashMap();
        localReposMap.put(local1.getKey(), local1);
        cc.setLocalRepositoriesMap(localReposMap);

        ProxyDescriptor proxy = new ProxyDescriptor();
        proxy.setHost("localhost");
        proxy.setKey("proxy");
        proxy.setPort(8987);
        cc.setProxies(Arrays.asList(proxy));

        HttpRepoDescriptor httpRepo = new HttpRepoDescriptor();
        httpRepo.setKey("http");
        httpRepo.setProxy(proxy);
        httpRepo.setUrl("http://blabla");
        Map<String, RemoteRepoDescriptor> map = Maps.newLinkedHashMap();
        map.put(httpRepo.getKey(), httpRepo);
        cc.setRemoteRepositoriesMap(map);

        // property sets
        PropertySet propSet = new PropertySet();
        propSet.setName("propSet1");
        Property prop = new Property();
        prop.setName("prop1");
        PredefinedValue value1 = new PredefinedValue();
        value1.setValue("value1");
        prop.addPredefinedValue(value1);
        PredefinedValue value2 = new PredefinedValue();
        value2.setValue("value2");
        prop.addPredefinedValue(value2);
        propSet.addProperty(prop);
        cc.addPropertySet(propSet);

        local1.addPropertySet(propSet);

        return cc;
    }
}
