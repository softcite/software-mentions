package org.grobid.core.utilities;

/**
 * Utility class for validating configuration objects and common validation patterns.
 * Provides centralized methods for configuration validation.
 */
public class ConfigurationValidator {

    /**
     * Validates if a software configuration has valid models for software processing.
     * Checks for either "software" or "software-type" models.
     *
     * @param configuration the configuration to validate
     * @return true if the configuration is not null and has valid models
     */
    public static boolean hasValidSoftwareModels(SoftwareConfiguration configuration) {
        return configuration != null &&
               (configuration.getModel("software") != null ||
                configuration.getModel("software-type") != null);
    }

    /**
     * Validates if a software configuration is valid with models collection.
     *
     * @param configuration the configuration to validate
     * @return true if the configuration is not null and has models
     */
    public static boolean isValidConfiguration(SoftwareConfiguration configuration) {
        return configuration != null &&
               configuration.getModels() != null &&
               !configuration.getModels().isEmpty();
    }
}