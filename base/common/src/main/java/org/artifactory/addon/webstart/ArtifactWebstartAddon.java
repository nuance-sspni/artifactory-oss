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

package org.artifactory.addon.webstart;

import org.artifactory.addon.Addon;
import org.artifactory.exception.InvalidCertificateException;

import java.io.File;
import java.io.IOException;
import java.security.Key;
import java.security.KeyStore;
import java.util.List;

/**
 * Webstart addon factory.
 *
 * @author Yossi Shaul
 */
public interface ArtifactWebstartAddon extends Addon {
    // Hardcoded alias prefix for client SSL certificates
    String SSL_CERT_ALIAS_PREFIX = "artsslcert:";

    KeyStore loadKeyStore(File keyStoreFile, String password);

    String getKeystorePassword();

    KeyStore getExistingKeyStore() throws KeyStoreNotFoundException;

    Key getAliasKey(KeyStore keyStore, String alias, String password);

    void addKeyPair(File file, String pairName, String keyStorePassword, String alias, String privateKeyPassword) throws IOException;

    boolean keyStoreExist();

    List<String> getKeyPairNames();

    boolean hasKeyPair(String keyPairName);

    boolean removeKeyPair(String keyPairName);

    void setKeyStorePassword(String password);

    void removeKeyStorePassword();

    void addPemCertificateToKeystore(String pemContent, String alias) throws InvalidCertificateException;
}
