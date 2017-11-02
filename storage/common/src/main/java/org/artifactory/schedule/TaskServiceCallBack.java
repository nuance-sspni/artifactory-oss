package org.artifactory.schedule;

/**
 * @author Inbar Tal
 */
public interface TaskServiceCallBack {
    void cancelMySelfCallback(TaskBase taskBase);
}
