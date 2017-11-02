package org.artifactory.storage.db.aql.sql.builder.query.aql;

import org.artifactory.aql.model.*;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlTable;
import org.artifactory.storage.db.aql.sql.model.AqlFieldExtensionEnum;

import java.util.List;

/**
 * In SimplePropertyCriterion we search for either specific KEY OR specific VALUE.
 * This is used by Postgres optimizer in order to use the properties table index properly in the VALUE case.
 * Note: At this point we know we are in either EQUALS or NOT-EQUALS criterion, and also node_props table only.
 * Also we know its a criterion on VALUE only.
 *
 * @author Yuval Reches
 */
public class PostgresSimplePropertyCriterion extends SimplePropertyCriterion {
    static final String POSTGRES_PROPERTY_VALUE_PARAM = "substr(?, 1, 255)";

    public PostgresSimplePropertyCriterion(List<AqlDomainEnum> subDomains,
            AqlVariable variable1,
            SqlTable table1, String comparatorName,
            AqlVariable variable2,
            SqlTable table2, boolean mspOperator) {
        super(subDomains, variable1, table1, comparatorName, variable2, table2, mspOperator);
    }

    /**
     * Decorate property value *field* with substr(..) in order to force Postgres to use the table index properly.
     * We check for the field name even though is SimplePropertyCriterion (ergo should be only prop_value field)
     * That is since in the NotEquals query we reach this method with node_id field as well.
     */
    @Override
    protected String fieldToSql(String index, AqlVariable variable) {
        AqlFieldEnum fieldEnum = ((AqlField) variable).getFieldEnum();
        if (fieldEnum == AqlPhysicalFieldEnum.propertyValue) {
            return postgresSubstr(super.fieldToSql(index, variable));
        }
        return super.fieldToSql(index, variable);
    }

    @Override
    protected String fieldToSql(String index, String fieldName) {
        String propValueFieldName = AqlFieldExtensionEnum.getExtensionFor(AqlPhysicalFieldEnum.propertyValue).tableField.toString();
        if (fieldName.equals(propValueFieldName)) {
            return postgresSubstr(super.fieldToSql(index, fieldName));
        }
        return super.fieldToSql(index, fieldName);
    }

    /**
     * Decorate property *values* with substr(..) in order to force Postgres to use the table index properly.
     */
    @Override
    protected String paramToSql(AqlVariable variable) {
        return POSTGRES_PROPERTY_VALUE_PARAM;
    }

    static String postgresSubstr(String original) {
        return "substr(" + original + "::text, 1, 255)";
    }

}