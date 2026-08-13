package org.grobid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the {@code useJUnitPlatform()} call in build.gradle.
 *
 * <p>Without it Gradle runs the tests with the JUnit 4 runner, which cannot see a JUnit 5 test
 * at all: the class is silently not discovered rather than failing, so a Jupiter test looks like
 * it passes when in fact it never ran. This test is written with the Jupiter API precisely so
 * that it disappears from the report if the platform is ever turned off again - the test count
 * drops instead of a test failing, which is the symptom to watch for.
 */
class JUnitPlatformTest {

    @Test
    void theJUnitPlatform_shouldBeEnabled() {
        assertTrue(true, "if this test is missing from the report, useJUnitPlatform() is gone");
    }
}
