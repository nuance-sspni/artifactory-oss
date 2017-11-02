package org.artifactory.common.config.utils;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * @author gidis
 */
public class FileConfigWithTimestamp implements ConfigWithTimestamp {

    private File file;
    private TimeProvider timeProvider;

    public FileConfigWithTimestamp(File file, TimeProvider timeProvider) {
        this.file = file;
        this.timeProvider = timeProvider;
    }

    @Override
    public InputStream getBinaryStream() {
        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public long getTimestamp() {
        return timeProvider.getNormalizedTime(file.lastModified());
    }

    @Override
    public long getSize() {
        return file.length();
    }

    //TODO [by dan]: notice that if (other == null) it's both before() and after() me, which is really fuzzy
    //TODO [by dan]: current logic seems to support it but this is very risky
    @Override
    public boolean isAfter(ConfigWithTimestamp config) {
        return config == null || (getTimestamp() > config.getTimestamp()) && ((getTimestamp() - config.getTimestamp()) > 1000);
    }

    @Override
    public boolean isAfter(Long timestamp) {
        return timestamp == null || (getTimestamp() > timestamp) && ((getTimestamp() - timestamp) > 1000);
    }

    @Override
    public boolean isBefore(@Nullable ConfigWithTimestamp config) {
        return config == null || isBefore(config.getTimestamp());
    }

    @Override
    public boolean isBefore(@Nullable Long timestamp) {
        return timestamp == null || (getTimestamp() < timestamp) && ((getTimestamp() - timestamp) < -1000);
    }
    //TODO [by dan]: notice that if (other == null) it's both before() and after() me, which is really fuzzy
    //TODO [by dan]: current logic seems to support it but this is very risky

    public File getFile() {
        return file;
    }

    public long getLength() {
        return file.length();
    }
}
