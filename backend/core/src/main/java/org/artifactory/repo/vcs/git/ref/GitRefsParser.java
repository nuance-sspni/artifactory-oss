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

package org.artifactory.repo.vcs.git.ref;

import org.apache.commons.lang.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the response of <a href="https://github.com/jquery/jquery.git/info/refs?service=git-upload-pack">git refs GET command</a>
 * And creates a list of tags and branches.
 *
 * @author Shay Yaakov
 */
public abstract class GitRefsParser {
    private static final Pattern TAGS_PATTERN = Pattern.compile("^(?:[a-f0-9]{4})([a-f0-9]+)\\s+refs\\/tags\\/(\\S+)");
    private static final Pattern HEADS_PATTERN = Pattern.compile("^(?:[a-f0-9]{4})([a-f0-9]+)\\s+refs\\/heads\\/(\\S+)");

    public static GitRefs parse(InputStream stream) throws IOException {
        GitRefs refs = new GitRefs();

        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        String line;
        while ((line = reader.readLine()) != null) {
            Matcher tagsMather = TAGS_PATTERN.matcher(line);
            if (tagsMather.matches()) {
                String tagName = tagsMather.group(2);
                if (!StringUtils.endsWith(tagName, "^{}")) {
                    refs.tags.add(new GitRef(tagName, tagsMather.group(1), false));
                }
            } else {
                Matcher headsMather = HEADS_PATTERN.matcher(line);
                if (headsMather.matches()) {
                    refs.branches.add(new GitRef(headsMather.group(2), headsMather.group(1), true));
                }
            }
        }

        return refs;
    }
}