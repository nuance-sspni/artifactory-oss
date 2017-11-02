package org.artifactory.environment.converter.shared.version.v1;

import org.apache.commons.io.FileUtils;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.ha.HaNodeProperties;
import org.artifactory.environment.converter.BasicEnvironmentConverter;
import org.artifactory.version.ArtifactoryVersion;
import org.artifactory.version.CompoundVersionDetails;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * @author Gidi Shabat
 */
public abstract class NoNfsBasicEnvironmentConverter implements BasicEnvironmentConverter {

    public static File resolveClusterHomeDir(ArtifactoryHome artifactoryHome) {
        File haNodePropertiesFile = artifactoryHome.getArtifactoryHaNodePropertiesFile();
        if (haNodePropertiesFile.exists()) {
            Properties haNodeProperties;
            try {
                haNodeProperties = new Properties();
                haNodeProperties.load(new FileInputStream(haNodePropertiesFile));
            } catch (Exception e) {
                throw new RuntimeException("Failed to load HA node properties from file: " + haNodePropertiesFile.getAbsolutePath());
            }
            String clusterHomePath = (String) haNodeProperties.get("cluster.home");
            if (clusterHomePath == null) {
                // Return null assuming that NoNfs conversion has beans successfully finished or no conversion needed
                return null;
            }
            File clusterHomeDir = new File(clusterHomePath);
            if (!clusterHomeDir.exists()) {
                throw new RuntimeException("Couldn't locate the cluster home dir, expecting it to be in: " +
                        clusterHomeDir.getAbsolutePath());
            }
            return clusterHomeDir;
        }
        return null;
    }

    public static File resolveClusterDataDir(ArtifactoryHome artifactoryHome) {
        File haNodePropertiesFile = artifactoryHome.getArtifactoryHaNodePropertiesFile();
        if (haNodePropertiesFile.exists()) {
            Properties haNodeProperties;
            try {
                haNodeProperties = new Properties();
                haNodeProperties.load(new FileInputStream(haNodePropertiesFile));
            } catch (Exception e) {
                throw new RuntimeException("Failed to load HA node properties from file: "
                        + haNodePropertiesFile.getAbsolutePath());
            }
            String clusterDataDir = (String) haNodeProperties.get(HaNodeProperties.PROP_HA_DATA_DIR);
            if (clusterDataDir != null) {
                return new File(clusterDataDir);
            }
            // Old cluster home property
            String clusterHomePath = (String) haNodeProperties.get("cluster.home");
            if (clusterHomePath == null) {
                // Return null assuming that NoNfs conversion has beans successfully finished or no conversion needed
                return null;
            }
            File clusterHomeDir = new File(clusterHomePath);
            if (!clusterHomeDir.exists()) {
                throw new RuntimeException("Couldn't locate the cluster home dir, expecting it to be in: " +
                        clusterHomeDir.getAbsolutePath());
            }
            return new File(clusterHomeDir, "ha-data");
        }
        return null;
    }

    public static void saveFileOrDirectoryAsBackup(File file) throws IOException {
        if (file.exists()) {
            String backupItemName = file.getName() + BACKUP_FILE_EXT;
            File backupItem = new File(file.getParentFile(), backupItemName);
            if (backupItem.exists()) {
                // Rename backup file so that we would be able to use its name
                String newBackupItemName = findBackupItemName(file);
                File newBackupItem = new File(file.getParentFile(), newBackupItemName);
                if (backupItem.isDirectory()) {
                    FileUtils.moveDirectory(backupItem, newBackupItem);
                } else {
                    FileUtils.moveFile(backupItem, newBackupItem);
                }
            }
            if (file.isDirectory()) {
                FileUtils.moveDirectory(file, backupItem);
            } else {
                FileUtils.moveFile(file, backupItem);
            }
        }
    }

    private static String findBackupItemName(File file) {
        int index = 1;
        String tempBackupItemName = file.getName() + BACKUP_FILE_EXT + "." + index;
        File tempBackupItem = new File(file.getParentFile(), tempBackupItemName);
        while (tempBackupItem.exists()) {
            index++;
            tempBackupItemName = file.getName() + BACKUP_FILE_EXT + "." + index;
            tempBackupItem = new File(file.getParentFile(), tempBackupItemName);
        }
        return tempBackupItemName;
    }

    public static boolean isUpgradeTo5x(CompoundVersionDetails source, CompoundVersionDetails target) {
        return source.getVersion().before(ArtifactoryVersion.v500beta1) && ArtifactoryVersion.v500beta1.beforeOrEqual(target.getVersion());
    }

    @Override
    public void convert(ArtifactoryHome artifactoryHome, CompoundVersionDetails source, CompoundVersionDetails target) {
        File clusterHomeDir = resolveClusterHomeDir(artifactoryHome);
        doConvert(artifactoryHome, clusterHomeDir);
    }

    protected abstract void doConvert(ArtifactoryHome artifactoryHome, File clusterHomeDir);

    public static void safeCopyRelativeFile(@Nonnull File clusterHomeDir, File targetFile) {
        File oldFile = new File(clusterHomeDir, "ha-etc/" + targetFile.getName());
        safeCopyFile(oldFile, targetFile);
    }

    public static void safeCopyFile(@Nonnull File srcFile, @Nonnull File targetFile) {
        try {
            if (srcFile.exists()) {
                saveFileOrDirectoryAsBackup(targetFile);
                // Make sure it's a new file or the DB system will override it.
                // TODO: [by fsi] May be found a better system here.
                FileUtils.copyFile(srcFile, targetFile, false);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy file " + srcFile.getAbsolutePath() + " to "
                    + targetFile.getAbsolutePath(), e);
        }
    }

    protected void safeMoveDirectory(@Nonnull File clusterHomeDir, File targetDirectory) {
        File oldDir = new File(clusterHomeDir, "ha-etc/" + targetDirectory.getName());
        try {
            if (oldDir.exists()) {
                saveFileOrDirectoryAsBackup(targetDirectory);
                if(!targetDirectory.exists()) {
                    FileUtils.forceMkdir(targetDirectory);
                }
                FileUtils.copyDirectory(oldDir, targetDirectory);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to move directory " + oldDir.getAbsolutePath() + " to "
                    + targetDirectory.getAbsolutePath(), e);
        }
    }
}
