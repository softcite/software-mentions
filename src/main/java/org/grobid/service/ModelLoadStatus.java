package org.grobid.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simple process-wide registry that tracks the outcome of eager model
 * loading performed at service startup.
 *
 * <p>Software-mentions' DeLFT-based classifier models (context, type,
 * used/created/shared) are instantiated directly via
 * {@code DeLFTClassifierModel}, bypassing grobid-core's
 * {@code TaggerFactory} bookkeeping. Because of this,
 * {@code TaggerFactory.getLoadedModels()} / {@code getFailedModels()} alone
 * cannot describe the health of the service. This class fills that gap for
 * the classifier models and can also be used to track eager loading of
 * CRF-based software parsers.
 */
public final class ModelLoadStatus {

    private static final Map<String, String> LOADED = new LinkedHashMap<>();
    private static final Map<String, String> FAILED = new LinkedHashMap<>();

    private ModelLoadStatus() {
    }

    public static synchronized void markLoaded(String modelName) {
        FAILED.remove(modelName);
        LOADED.put(modelName, "ok");
    }

    public static synchronized void markFailed(String modelName, String reason) {
        LOADED.remove(modelName);
        FAILED.put(modelName, reason == null ? "unknown error" : reason);
    }

    public static synchronized Map<String, String> getLoadedModels() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(LOADED));
    }

    public static synchronized Map<String, String> getFailedModels() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(FAILED));
    }

    public static synchronized boolean hasFailures() {
        return !FAILED.isEmpty();
    }

    public static synchronized void reset() {
        LOADED.clear();
        FAILED.clear();
    }
}
