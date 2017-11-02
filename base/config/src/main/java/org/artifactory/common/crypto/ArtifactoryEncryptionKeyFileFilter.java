package org.artifactory.common.crypto;

import org.artifactory.common.ConstantValues;

import java.io.File;
import java.io.FileFilter;

/**
 * @author Dan Feldman
 * A {@link FileFilter} that accepts all files of the form <code>artifactory.key.*</code>.
 * See {@link ConstantValues#securityMasterKeyLocation)}
 */
public class ArtifactoryEncryptionKeyFileFilter implements FileFilter {

    private final String keyFileName;

    public ArtifactoryEncryptionKeyFileFilter(String keyFileName) {
        this.keyFileName = keyFileName;
    }

    @Override
    public boolean accept(File pathname) {
        // Enough for the full path to contain the key location, I don't want endsWith because we save key files
        // with timestamps at the end by default.
        return pathname != null && pathname.getAbsolutePath().contains(keyFileName);
    }
}
