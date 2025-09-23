package org.grobid.core.utilities;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class Versioner {
    public static final Logger LOGGER = LoggerFactory.getLogger(Versioner.class);

    private static final String VERSION_FILE = "/version.txt";
    private static final String REVISION_FILE = "/revision.txt";
    private static final String UNKNOWN_VERSION_STR = "unknown";

    // Version
    private static String VERSION = null;
    private static String REVISION = null;

    public static String getVersion() {
        if (VERSION != null) {
            return VERSION;
        }
        synchronized (GrobidProperties.class) {
            if (VERSION == null) {
                VERSION = readFromSystemPropertyOrFromFile("project.version", VERSION_FILE);
            }
        }
        return VERSION;
    }

    public static String getRevision() {
        if (REVISION != null) {
            return REVISION;
        }
        synchronized (GrobidProperties.class) {
            if (REVISION == null) {
                REVISION = readFromSystemPropertyOrFromFile("gitRevision", REVISION_FILE);
            }
        }
        return REVISION;
    }

    private static String readFromSystemPropertyOrFromFile(String systemPropertyName, String filePath) {
        String grobidVersion = UNKNOWN_VERSION_STR;
        String systemPropertyValue = System.getProperty(systemPropertyName);
        if (systemPropertyValue != null) {
            grobidVersion = systemPropertyValue;
        } else {
            try (InputStream is = GrobidProperties.class.getResourceAsStream(filePath)) {
                String grobidVersionTmp = IOUtils.toString(is, StandardCharsets.UTF_8);
                if (!StringUtils.startsWithIgnoreCase(grobidVersionTmp, "${project_")) {
                    grobidVersion = grobidVersionTmp;
                }
            } catch (IOException e) {
                LOGGER.error("Cannot read the version from resources", e);
            }
        }
        return grobidVersion;
    }
}
