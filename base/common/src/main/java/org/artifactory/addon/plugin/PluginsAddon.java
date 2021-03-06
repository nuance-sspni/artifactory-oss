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

package org.artifactory.addon.plugin;

import org.artifactory.addon.Addon;
import org.artifactory.api.config.ImportableExportable;
import org.artifactory.build.staging.BuildStagingStrategy;
import org.artifactory.request.Request;
import org.artifactory.resource.ResourceStreamHandle;

import javax.annotation.Nullable;
import java.io.Reader;
import java.util.List;
import java.util.Map;

/**
 * @author Yoav Landman
 * @date Oct 28, 2008
 */
public interface PluginsAddon extends Addon, ImportableExportable {
    String PLUGIN_STATUS_LOADED = "Loaded";
    String PLUGIN_STATUS_ERROR = "Error";

    <C> void execPluginActions(Class<? extends PluginAction> type, C context, Object... args);

    ResponseCtx execute(String executionName, String method, Map params, @Nullable ResourceStreamHandle body,
            boolean async);

    BuildStagingStrategy getStagingStrategy(String strategyName, String buildName, Map params);

    Map<String, List<PluginInfo>> getPluginInfo(@Nullable String pluginType);

    ResponseCtx promote(String promotionName, String buildName, String buildNumber, Map params);

    ResponseCtx deployPlugin(Reader pluginContent, String scriptName);

    /**
     * Reloads user plugins. Nothing is reloaded if there's no plugin present or no plugin modified since the last reload.
     *
     * @return Response context with status for various reloaded user plugins.
     */
    ResponseCtx reloadPlugins();

    void executeAdditiveRealmPlugins(Request servletRequest);

    /**
     * Used by the UI to get info about all plugins in the plugin directory (loaded and otherwise).
     */
    Map<String, String> getPluginsStatus();

    /**
     *  Used by the Support Bundle Service to append plugins info.
     */
    String getPluginsInfoSupportBundleDump();
}
