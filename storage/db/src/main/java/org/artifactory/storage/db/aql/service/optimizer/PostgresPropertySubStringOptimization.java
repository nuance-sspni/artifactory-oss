package org.artifactory.storage.db.aql.service.optimizer;

import com.google.common.collect.Lists;
import org.artifactory.aql.AqlFieldResolver;
import org.artifactory.aql.model.AqlComparatorEnum;
import org.artifactory.aql.model.AqlField;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.AqlValue;
import org.artifactory.storage.db.aql.sql.builder.query.aql.*;
import org.artifactory.storage.db.aql.sql.model.SqlTableEnum;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * The optimizer searches for *EQUALS* or *NOT EQUALS* criterion on node_props that has property *VALUE* as part of it.
 * In Postgres we need to decorate the value with substr(..) in order to use the table index.
 *
 * It can be either SimplePropertyCriterion == searches for specific property VALUE (doesn't matter which KEY)
 * or ComplexPropertyCriterion == searches for [ (specific property KEY) AND (specific property VALUE) ]
 * When we find one of these criteria we replace it with the respective optimized one.
 *
 * In case the *VALUE* length is bigger than the node_props.prop_value index length, we add ANOTHER EQUALS criterion.
 * The criterion will validate that the results has no irrelevant rows. (substr(field)=substr(val) AND field=val)
 * The index of the table (substr) will be the first to filter, and then the normal equals.
 *
 * @author Yuval Reches
 */
public class PostgresPropertySubStringOptimization extends OptimizationStrategy {

    /**
     * The length of the index as defined in postgresql.sql
     */
    private static final int POSTGRES_PROP_VALUE_INDEX_LENGTH = 255;

    /**
     * Optimizing the query in case the criterion is:
     * a. on a property value
     * b. on a property value of node_props table
     * c. either EQUALS or NOT EQUALS
     */
    @Override
    public void optimize(AqlQuery aqlQuery, String transformation) {
        List<AqlQueryElement> aqlElements = Lists.newArrayList();
        // Copy all elements and try to find candidates for replacement
        for (int i = 0; i < transformation.length(); i++) {
            AqlQueryElement aqlQueryElement = aqlQuery.getAqlElements().get(i);
            PostgresCriterionType postgresCriterionType = findCriterionReplacementClass(i, transformation,
                    aqlQueryElement);
            // In case replacement is not null the we need to replace the current criterion with an optimized one
            if (postgresCriterionType != null) {
                Criterion criterion = (Criterion) aqlQueryElement;
                String valueString = (String) ((AqlValue) criterion.getVariable2()).toObject();
                // Check if we need extra EQUALS criterion (to avoid irrelevant results due to substring() on value).
                // The extra criterion is required when the value is longer than max length and when the criterion uses equals.
                // If so We wrap both of the them in parenthesis --> ( substr(field)=substr(val) AND field=val)
                if (needExtraEqualsCriterion(valueString, criterion)) {
                    aqlElements.add(AqlAdapter.open);
                    // Add the optimized criterion
                    aqlElements.add(createPostgresCriterion(postgresCriterionType, criterion));
                    aqlElements.add(AqlAdapter.and);
                    // Add the extra EQUALS criterion in order to verify
                    aqlElements.add(createPropertyValueEqualsElement(criterion));
                    aqlElements.add(AqlAdapter.close);
                } else {
                    // Value is short, add only the optimized criterion
                    aqlElements.add(createPostgresCriterion(postgresCriterionType, criterion));
                }
            } else {
                // No need to optimize, add the criterion as-is.
                aqlElements.add(aqlQueryElement);
            }
        }
        aqlQuery.getAqlElements().clear();
        aqlQuery.getAqlElements().addAll(aqlElements);
    }

    /**
     * Checks if the property *VALUE* length is bigger than the node_props.prop_value index length
     */
    private boolean needExtraEqualsCriterion(String valueString, Criterion criterion) {
        return valueString.length() >= POSTGRES_PROP_VALUE_INDEX_LENGTH &&
                comparatorFrom(criterion) == AqlComparatorEnum.equals;
    }


    /**
     * Searches for criteria relevant to the optimizer and replace it with the respective optimized class
     * (see documentation in top of the class)
     */
    private PostgresCriterionType findCriterionReplacementClass(int i, String transformation,
            AqlQueryElement queryElement) {
        char currentElement = transformation.charAt(i);
        // Be aware of the instance of property criterion (each type requires different class)
        PostgresCriterionType postgresCriterionType = currentElement == 'p' ? PostgresCriterionType.Complex :
                currentElement == 'v' || currentElement == 'V' ? PostgresCriterionType.Simple : null;
        // Skip irrelevant elements
        if (postgresCriterionType == null) {
            return null;
        }
        // This is complex property criterion, or simple property criterion, or simple criterion with property field
        Criterion criterion = (Criterion) queryElement;
        boolean nodePropsCriteria = SqlTableEnum.node_props == criterion.getTable1().getTable();
        // Checking if its the node_props table
        if (!nodePropsCriteria) {
            return null;
        }
        AqlComparatorEnum comparatorEnum = comparatorFrom(criterion);
        // Checking if its equals or not-equals criterion
        if (!isEqualityComparator(comparatorEnum)) {
            return null;
        }
        return postgresCriterionType;
    }

    private AqlComparatorEnum comparatorFrom(Criterion criterion) {
        return AqlComparatorEnum.value(criterion.getComparatorName());
    }

    private boolean isEqualityComparator(AqlComparatorEnum comparator) {
        return comparator == AqlComparatorEnum.equals || comparator == AqlComparatorEnum.notEquals;
    }

    private Criterion createPropertyValueEqualsElement(Criterion criterion) {
        // variable2 is the property value in both SimplePropertyCriteria and ComplexPropertyCriteria
        AqlValue variable2 = (AqlValue) criterion.getVariable2();
        AqlField propValue = AqlFieldResolver.resolve(AqlPhysicalFieldEnum.propertyValue);
        return new SimplePropertyCriterion(criterion.getSubDomains(),
                propValue, criterion.getTable1(), AqlComparatorEnum.equals.signature, variable2, criterion.getTable2(),
                criterion.isMspOperator());

    }

    private Criterion createPostgresCriterion(PostgresCriterionType type, Criterion criterion) {
        requireNonNull(type, "criterion type is required");
        switch (type) {
            case Complex:
                return new PostgresComplexPropertyCriterion(criterion.getSubDomains(),
                        criterion.getVariable1(), criterion.getTable1(), criterion.getComparatorName(),
                        criterion.getVariable2(), criterion.getTable2(), criterion.isMspOperator());
            case Simple:
                return new PostgresSimplePropertyCriterion(criterion.getSubDomains(),
                        criterion.getVariable1(), criterion.getTable1(), criterion.getComparatorName(),
                        criterion.getVariable2(), criterion.getTable2(), criterion.isMspOperator());
            default:
                throw new IllegalStateException("PostgreSQL optimizer got wrong criterion type for optimizing.");
        }
    }

    private enum PostgresCriterionType {
        Complex, Simple
    }

}