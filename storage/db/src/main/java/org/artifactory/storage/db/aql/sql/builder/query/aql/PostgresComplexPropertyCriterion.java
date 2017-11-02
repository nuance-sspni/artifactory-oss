package org.artifactory.storage.db.aql.sql.builder.query.aql;

import org.artifactory.aql.model.*;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlTable;
import org.artifactory.storage.db.aql.sql.model.AqlFieldExtensionEnum;

import java.util.List;

import static org.artifactory.storage.db.aql.sql.builder.query.aql.PostgresSimplePropertyCriterion.POSTGRES_PROPERTY_VALUE_PARAM;
import static org.artifactory.storage.db.aql.sql.builder.query.aql.PostgresSimplePropertyCriterion.postgresSubstr;

/**
 * In ComplexPropertyCriteria we search for both specific KEY AND specific VALUE
 * This is used by Postgres optimizer in order to use the properties table index properly.
 * Note: At this point we know we are in either EQUALS or NOT-EQUALS criterion, and also node_props table only.
 *
 * @author Yuval Reches
 */
public class PostgresComplexPropertyCriterion extends ComplexPropertyCriterion {

    public PostgresComplexPropertyCriterion(List<AqlDomainEnum> subDomains,
            AqlVariable variable1,
            SqlTable table1, String comparatorName,
            AqlVariable variable2,
            SqlTable table2, boolean mspOperator) {
        super(subDomains, variable1, table1, comparatorName, variable2, table2, mspOperator);
    }

    /**
     * Decorate property value *field* with substr(..) in order to force Postgres to use the table index properly.
     * We check for the field type and wrap it accordingly.
     * (Check is being done here and not only on "equals" method since we have special case in "notEquals" case)
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
        // we do nothing with the variable since it was already added to the params list in the prior method
        if(variable.equals(getVariable2())) {
            return POSTGRES_PROPERTY_VALUE_PARAM;
        }else {
            return super.paramToSql(variable);
        }
    }

}