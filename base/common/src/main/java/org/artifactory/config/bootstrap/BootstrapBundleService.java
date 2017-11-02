package org.artifactory.config.bootstrap;

import org.artifactory.spring.ReloadableBean;

import javax.annotation.Nonnull;
import java.io.File;

/**
 * Created by Yinon Avraham.
 */
public interface BootstrapBundleService extends ReloadableBean {

    /**
     * Create a bootstrap bundle and save it to a file
     * @return the file to which the bootstrap bundle was saved
     */
    @Nonnull
    File createBootstrapBundle();

}
