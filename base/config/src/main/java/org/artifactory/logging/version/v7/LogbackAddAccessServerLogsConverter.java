package org.artifactory.logging.version.v7;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang.StringUtils;
import org.artifactory.util.StringInputStream;
import org.artifactory.util.XmlUtils;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.*;
import org.jdom2.input.SAXBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Add the Access Server main appender, and Audit log appender + two loggers
 *
 * @author Shay Bagants
 */
public class LogbackAddAccessServerLogsConverter implements XmlConverter {

    private static final Logger log = LoggerFactory.getLogger(LogbackAddAccessServerLogsConverter.class);

    private Map<String, String> appendersToAdd = ImmutableMap.of(
            "JFROG_ACCESS_CONSOLE", LogbackAddAccessServerLogsConverterConstants.JFROG_ACCESS_CONSOLE,
            "JFROG_ACCESS", LogbackAddAccessServerLogsConverterConstants.JFROG_ACCESS,
            "JFROG_ACCESS_AUDIT", LogbackAddAccessServerLogsConverterConstants.JFROG_ACCESS_AUDIT);

    private Map<String, String> loggersToAdd = ImmutableMap.of(
            "com.jfrog.access", LogbackAddAccessServerLogsConverterConstants.JFROG_ACCESS_LOGGER,
            "com.jfrog.access.server.audit.TokenAuditor",
            LogbackAddAccessServerLogsConverterConstants.JFROG_ACCESS_TOKEN_AUDITOR_LOGGER);

    @Override
    public void convert(Document doc) {
        log.info("Starting logback conversion --> Adding logs JFrog Access logs.");
        Element root = doc.getRootElement();
        Namespace ns = root.getNamespace();

        // Adding 3 new JFrog Access appenders
        appendersToAdd.forEach((appenderName, appenderValue) -> {
            try {
                addAppender(root, ns, appenderName, appenderValue);
            } catch (IOException | JDOMException e) {
                logError(e, appenderName);
            }
        });

        // Adding two new loggers
        loggersToAdd.forEach((loggerName, loggerValue) -> {
            try {
                addLogger(root, ns, loggerName, loggerValue);
            } catch (IOException | JDOMException e) {
                logError(e, loggerName);

            }
        });

        log.info("JFrog Access logs logback conversion completed.");
    }

    private void addAppender(Element root, Namespace ns,
            String appenderNameToAdd, String appenderContentToAdd)
            throws IOException, JDOMException {
        List<Element> appenders = root.getChildren("appender", ns);
        for (Element element : appenders) {
            if (element.getAttributeValue("name", ns).equals(appenderNameToAdd)) {
                log.info(appenderNameToAdd + " log appender already exists in logback.xml, skipping conversion");
                return;
            }
        }
        Element appender = getElement(appenderContentToAdd);
        root.addContent(root.indexOf(appenders.get(appenders.size() - 1)) + 1, new Text("\n    "));
        root.addContent(root.indexOf(appenders.get(appenders.size() - 1)) + 2, appender);
    }

    private void addLogger(Element root, Namespace ns, String loggerNameToAdd, String loggerContentToAdd)
            throws IOException, JDOMException {
        List<Element> loggers = root.getChildren("logger", ns);
        for (Element logger : loggers) {
            if (StringUtils.equals(logger.getAttributeValue("name", ns), loggerNameToAdd)) {
                log.info("Logger: '" + loggerNameToAdd + "' config already exists in logback.xml, skipping conversion");
                return;
            }
        }
        Element logger = getElement(loggerContentToAdd);
        root.addContent(root.indexOf(loggers.get(loggers.size() - 1)) + 1, new Text("\n    "));
        root.addContent(root.indexOf(loggers.get(loggers.size() - 1)) + 2, logger);
    }

    /**
     * Return Element object from String
     */
    private Element getElement(String input) throws IOException, JDOMException {
        SAXBuilder builder = XmlUtils.createSaxBuilder();
        try (InputStream stream = new StringInputStream(input)) {
            Document doc = builder.build(stream);
            Element root = doc.getRootElement();
            return root.detach();
        }
    }

    private void logError(Exception e, String elementName) {
        String err = "Error adding the '" + elementName + "' element to logback.xml:";
        log.error(err + e.getMessage());
        log.debug(err, e);
    }
}
