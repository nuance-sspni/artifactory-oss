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

package org.artifactory.util.stream;

import com.google.common.collect.Multimap;

import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 * @author Dan Feldman
 */
public class MultimapCollector {

    public static <K, V, A extends Multimap<K, V>> Collector<Map.Entry<K, V>, A, A> toMultimap(Supplier<A> supplier) {
        return Collector.of(supplier, (acc, entry) -> acc.put(entry.getKey(), entry.getValue()), (map1, map2) -> {
            map1.putAll(map2);
            return map1;
        });
    }
}
