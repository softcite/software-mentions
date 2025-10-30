package org.grobid.core.utilities;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Collections;

/**
 * Utility class for text normalization and common validation operations.
 * Provides centralized methods for text cleaning, validation, and collection checks.
 */
public class TextNormalizationUtils {

    /**
     * Normalizes whitespace in text by replacing newlines and tabs with spaces,
     * then consolidating multiple spaces into single spaces and trimming.
     *
     * @param text the input text to normalize
     * @return normalized text, or null if input is null
     */
    public static String normalizeTextWhitespace(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("\\n", " ")
                   .replaceAll("\\t", " ")
                   .replaceAll("( )+", " ")
                   .trim();
    }

    /**
     * Removes dash patterns followed by spaces or newlines.
     *
     * @param text the input text to process
     * @return text with dash patterns removed, or null if input is null
     */
    public static String removeDashPatterns(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("-( |\\n)*", "");
    }

    /**
     * Checks if a collection is null or empty using CollectionUtils.isEmpty().
     * This is a centralized method for consistency.
     *
     * @param collection the collection to check
     * @return true if the collection is null or empty
     */
    public static boolean isEmpty(Collection<?> collection) {
        return CollectionUtils.isEmpty(collection);
    }

    /**
     * Checks if a collection is not null and not empty using CollectionUtils.isNotEmpty().
     * This is a centralized method for consistency.
     *
     * @param collection the collection to check
     * @return true if the collection is not null and not empty
     */
    public static boolean isNotEmpty(Collection<?> collection) {
        return CollectionUtils.isNotEmpty(collection);
    }

    /**
     * Returns an empty list if the input list is null or empty.
     *
     * @param list the input list
     * @param <T> the type of elements in the list
     * @return the input list if not empty, otherwise an empty list
     */
    public static <T> List<T> emptyIfNull(List<T> list) {
        return CollectionUtils.isEmpty(list) ? Collections.emptyList() : list;
    }

    /**
     * Checks if a string is null, empty, or contains only whitespace.
     *
     * @param text the string to check
     * @return true if the string is null, empty, or whitespace-only
     */
    public static boolean isBlank(String text) {
        return StringUtils.isBlank(text);
    }

    /**
     * Checks if a string is not null, empty, and contains more than just whitespace.
     *
     * @param text the string to check
     * @return true if the string is not null, empty, or whitespace-only
     */
    public static boolean isNotBlank(String text) {
        return StringUtils.isNotBlank(text);
    }
}