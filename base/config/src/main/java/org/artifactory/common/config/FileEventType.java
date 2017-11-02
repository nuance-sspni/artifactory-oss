package org.artifactory.common.config;

import java.nio.file.WatchEvent;

/**
 * Loosely based on {@link java.nio.file.StandardWatchEventKinds} for our convenience.
 * If something ever gets messed up with our file sync events and you reach here, check that they didn't change the
 * strings in {@link WatchEvent.Kind#name()}.
 * @author Dan Feldman
 */
public enum FileEventType {

    DELETE("ENTRY_DELETE"), CREATE("ENTRY_CREATE"), MODIFY("ENTRY_MODIFY");

    String eventTypeName;

    FileEventType(String eventTypeName) {
        this.eventTypeName = eventTypeName;
    }

    public String getName() {
        return eventTypeName;
    }

    public static FileEventType fromValue(String eventTypeName) {
        switch (eventTypeName) {
            case "ENTRY_DELETE" :
                return DELETE;
            case "ENTRY_CREATE" :
                return CREATE;
            case "ENTRY_MODIFY":
                return MODIFY;
        }
        throw new IllegalArgumentException("No such file event type '" + eventTypeName + "'");
    }

    @Override
    public String toString() {
        switch (eventTypeName) {
            case "ENTRY_DELETE" :
                return "delete";
            case "ENTRY_CREATE" :
                return "create";
            case "ENTRY_MODIFY":
                return "modify";
        }
        throw new IllegalArgumentException("No such file event type '" + eventTypeName + "'");
    }
}
