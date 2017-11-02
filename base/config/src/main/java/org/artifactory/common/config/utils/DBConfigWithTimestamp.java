package org.artifactory.common.config.utils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * @author gidis
 */
public class DBConfigWithTimestamp implements ConfigWithTimestamp {

    private byte[] content;
    private long timestamp;
    private String name;

    public DBConfigWithTimestamp(byte[] content, long timestamp, String name) {
        this.content = content;
        this.timestamp = timestamp;
        this.name = name;
    }

    @Override
    public InputStream getBinaryStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public long getSize() {
        throw new UnsupportedOperationException("DBConfigWithTimestamp doesn't support the 'getSize()' method");
    }

    //TODO [by dan]: notice that if (other == null) it's both before() and after() me, which is really fuzzy
    //TODO [by dan]: current logic seems to support it but this is very risky
    @Override
    public boolean isBefore(ConfigWithTimestamp config) {
        return config == null || timestamp < config.getTimestamp();
    }

    @Override
    public boolean isBefore(Long timestamp) {
        return timestamp == null || this.timestamp < timestamp;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean isAfter(ConfigWithTimestamp config) {
        return config == null || timestamp > config.getTimestamp();
    }

    @Override
    public boolean isAfter(Long timestamp) {
        return timestamp == null || this.timestamp > timestamp;
    }
    //TODO [by dan]: notice that if (other == null) it's both before() and after() me, which is really fuzzy
    //TODO [by dan]: current logic seems to support it but this is very risky
}
