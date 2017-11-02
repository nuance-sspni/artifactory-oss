package org.artifactory.aql.action;

import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.aql.model.AqlActionEnum;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.result.AqlRestResult.Row;
import org.artifactory.aql.util.AqlUtils;
import org.artifactory.descriptor.property.Property;
import org.artifactory.repo.RepoPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

import static org.artifactory.aql.action.AqlActionException.Reason.ACTION_FAILED;
import static org.artifactory.aql.action.AqlActionException.Reason.UNEXPECTED_CONTENT;
import static org.artifactory.aql.action.AqlActionException.Reason.UNSUPPORTED_FOR_DOMAIN;

/**
 * Updates the property for a given row
 *
 * @author Dan Feldman
 */
public class AqlUpdateItemPropertyAction implements AqlPropertyAction {
    private static final Logger log = LoggerFactory.getLogger(AqlUpdateItemPropertyAction.class);

    private boolean dryRun = true;
    private List<String> propKeys = Lists.newArrayList();
    private String newValue;

    @Override
    public Row doAction(Row row) throws AqlActionException {
        //Should also be verified in query construction phase but just in case.
        if (!AqlDomainEnum.properties.equals(row.getDomain())) {
            String msg = "Skipping delete action for row, only properties domain is supported - row has domain: "
                    + row.getDomain();
            log.debug(msg);
            throw new AqlActionException(msg, UNSUPPORTED_FOR_DOMAIN);
        }
        if (propKeys.isEmpty()) {
            String msg = "Skipping update property action for row, missing required property key inclusion";
            log.debug(msg);
            throw new AqlActionException(msg, UNEXPECTED_CONTENT);
        }
        // Because this action is used with properties domain rows are in the inflated rows 'items' field
        if (StringUtils.isBlank(row.itemRepo) && StringUtils.isBlank(row.itemPath) && StringUtils.isBlank(row.itemName)) {
            if (row.items == null) {
                throw new AqlActionException("Cannot resolve artifact path from given row.",
                        AqlActionException.Reason.UNEXPECTED_CONTENT);
            }
            for (Row item : row.items) {
                updateRowProperties(AqlUtils.fromAql(item));
            }
        } else {
            updateRowProperties(AqlUtils.fromAql(row));
        }
        return row;
    }

    //TODO [by dan]: this is failfast without rollback, maybe better to do granular exception?
    private void updateRowProperties(RepoPath itemPath) throws AqlActionException {
        if (itemPath == null) {
            throw new AqlActionException("Cannot resolve artifact path from given row.",
                    AqlActionException.Reason.UNEXPECTED_CONTENT);
        }
        try {
            propKeys.stream()
                    .filter(Objects::nonNull)
                    .map(Property::new)
                    .forEach(prop -> getPropsService().editProperty(itemPath, null, prop, true, newValue));
        } catch (Exception e) {
            throw new AqlActionException("Failed to update properties '" + propKeys + "' for path " + itemPath
                    + " with new value '" + newValue +"'. Check the log for more info.", ACTION_FAILED);
        }
    }

    @Override
    public String getName() {
        return AqlActionEnum.updateProperty.name;
    }

    @Override
    public boolean supportsDomain(AqlDomainEnum domain) {
        return AqlDomainEnum.properties.equals(domain);
    }

    @Override
    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    @Override
    public boolean isDryRun() {
        return dryRun;
    }

    @Override
    public List<String> getKeys() {
        return propKeys;
    }

    @Override
    public void addKey(String key) {
        propKeys.add(key);
    }

    @Override
    public String getValue() {
        return newValue;
    }

    @Override
    public void setValue(String newValue) {
        this.newValue = newValue;
    }

    private PropertiesService getPropsService() {
        return ContextHelper.get().beanForType(PropertiesService.class);
    }
}
