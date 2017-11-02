package org.artifactory.log.logback;

import org.apache.commons.lang.StringUtils;
import org.artifactory.common.ArtifactoryHome;
import org.jfrog.common.logging.logback.LogbackContextConfigurator;
import org.jfrog.common.logging.logback.servlet.LoggerConfigInfo;

/**
 * @author Yinon Avraham.
 */
public class ArtifactoryLoggerConfigInfo extends LoggerConfigInfo {

    private final ArtifactoryHome artifactoryHome;

    public ArtifactoryLoggerConfigInfo(String contextId, ArtifactoryHome artifactoryHome) {
        super(contextId, artifactoryHome.getLogbackConfig());
        this.artifactoryHome = artifactoryHome;
    }

    @Override
    protected void configure(LogbackContextConfigurator configurator) {
        super.configure(configurator);
        configurator
                .property("artifactory.contextId", normalizedContextId())
                .property(ArtifactoryHome.SYS_PROP, artifactoryHome.getHomeDir().getAbsolutePath());
    }

    private String normalizedContextId() {
        String contextId = StringUtils.trimToEmpty(getContextId());
        contextId = "artifactory".equalsIgnoreCase(contextId) ? "" : contextId + " ";
        contextId = StringUtils.isBlank(contextId) ? "" : contextId;
        return StringUtils.uncapitalize(contextId);
    }
}
