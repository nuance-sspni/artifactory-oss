package org.artifactory.storage.db.conversion;

import org.artifactory.common.config.db.DbType;
import org.artifactory.storage.db.util.JdbcHelper;

import java.util.function.BiPredicate;

/**
 * Predicates for conditional conversion {@link org.artifactory.storage.db.version.converter.ConditionalDBSqlConverter}.
 *
 * @author Dan Feldman
 */
public interface ConversionPredicate {

    BiPredicate<JdbcHelper, DbType> condition();

}
