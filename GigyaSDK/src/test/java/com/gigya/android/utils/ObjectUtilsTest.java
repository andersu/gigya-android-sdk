package com.gigya.android.utils;

import com.gigya.android.sdk.utils.ObjectUtils;

import junit.framework.TestCase;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@SuppressWarnings("unchecked")
public class ObjectUtilsTest {

    @Test
    public void testFirstNonNull() {
        // Arrange
        final String notNull = "NotNull";
        final String isNull = null;
        // Act
        final String notNullCheck = ObjectUtils.firstNonNull(notNull, "Okay thanks");
        final String isNullCheck = ObjectUtils.firstNonNull(isNull, "No Im not null!");
        // Assert
        assertNotNull(notNullCheck);
        assertEquals("NotNull", notNull);
        assertNotNull(isNullCheck);
        assertEquals("No Im not null!", isNullCheck);
    }

    @Test
    public void testFlatObjectDifference() {
        // Arrange
        final Map<String, Object> original = new HashMap<String, Object>() {{
            put("one", 1);
            put("two", "Two");
            put("three", 3);
            put("four", 7);
        }};
        final Map<String, Object> updated = new HashMap<String, Object>() {{
            put("one", 1);
            put("two", "Updated");
            put("three", 3);
            put("four", 8);
        }};
        // Act
        final Map<String, Object> difference = ObjectUtils.objectDifference(original, updated);
        // Assert
        TestCase.assertEquals(2, difference.size());
        assertNotNull(difference.get("two"));
        assertEquals(difference.get("two"), "Updated");
        assertNotNull(difference.get("four"));
        assertEquals(difference.get("four"), 8);
    }

    @Test
    public void testObjectDifferenceWithInnerMapping() {
        // Arrange
        final Map<String, Object> original = new HashMap<String, Object>() {{
            put("one", 1);
            put("two", new HashMap<String, Object>() {{
                put("innerOne", 11);
                put("innerTwo", 12);
            }});
            put("three", 3);
        }};
        final Map<String, Object> updated = new HashMap<String, Object>() {{
            put("one", 1);
            put("two", new HashMap<String, Object>() {{
                put("innerOne", 11);
                put("innerTwo", 13);
            }});
            put("three", 3);
        }};
        // Act
        final Map<String, Object> difference = ObjectUtils.objectDifference(original, updated);
        // Assert
        TestCase.assertEquals(1, difference.size());
        assertNotNull(difference.get("two"));
        assertEquals(((Map<String, Object>) Objects.requireNonNull(difference.get("two"))).get("innerTwo"), 13);
    }

    @Test
    public void testObjectDifferenceWithIdenticalMaps() {
        // Arrange
        final Map<String, Object> original = new HashMap<String, Object>() {{
            put("one", 1);
            put("two", new HashMap<String, Object>() {{
                put("innerOne", 11);
                put("innerTwo", 12);
            }});
            put("three", 3);
        }};
        final Map<String, Object> updated = new HashMap<String, Object>() {{
            put("one", 1);
            put("two", new HashMap<String, Object>() {{
                put("innerOne", 11);
                put("innerTwo", 12);
            }});
            put("three", 3);
        }};
        // Act
        final Map<String, Object> difference = ObjectUtils.objectDifference(original, updated);
        // Assert
        TestCase.assertEquals(0, difference.size());
    }

    @Test
    public void testObjectDifferenceWithNulls() {
        // Arrange
        final Map<String, Object> original = new HashMap<String, Object>() {{
            put("one", 1);
            put("two", "Two");
            put("three", 3);
            put("four", 7);
        }};
        final Map<String, Object> updated = new HashMap<String, Object>() {{
            put("one", 1);
            put("two", null);
            put("three", 3);
            put("four", 8);
        }};
        // Act
        final Map<String, Object> difference = ObjectUtils.objectDifference(original, updated);
        // Assert
        TestCase.assertEquals(2, difference.size());
        assertNull(difference.get("two"));
    }

    @Test
    public void testObjectDifferenceWithOriginalNull() {
        // Arrange
        final Map<String, Object> original = new HashMap<String, Object>() {{
            put("one", 1);
            put("two", "Two");
            put("three", 3);
            put("four", 7);
        }};
        // Act
        final Map<String, Object> difference = ObjectUtils.objectDifference(original, null);
        // Assert
        assertEquals(0, difference.size());
    }

    @Test
    public void testObjectDifferenceWithUpdatedNull() {
        // Arrange
        final Map<String, Object> updated = new HashMap<String, Object>() {{
            put("one", 1);
            put("two", null);
            put("three", 3);
            put("four", 8);
        }};
        // Act
        final Map<String, Object> difference = ObjectUtils.objectDifference(null, updated);
        // Assert
        assertEquals(0, difference.size());
    }

    @Test
    public void testMergeRemovingDuplicates() {
        // Arrange
        final List<String> first = Arrays.asList("1", "2", "3", "4");
        final List<String> second = Arrays.asList("3", "4", "5", "6", "7");
        // Act
        final List<String> merged = ObjectUtils.mergeRemovingDuplicates(first, second);
        // Assert
        final List<String> assertion = Arrays.asList("1", "2", "3", "4", "5", "6", "7");
        assertArrayEquals(assertion.toArray(), merged.toArray());
    }

    @Test
    public void testMergeRemovingDuplicatesWithEmptyList() {
        // Arrange
        final List<String> first = Arrays.asList("1", "2", "3", "4");
        final List<String> second = new ArrayList<>();
        // Act
        final List<String> merged = ObjectUtils.mergeRemovingDuplicates(first, second);
        // Assert
        final List<String> assertion = Arrays.asList("1", "2", "3", "4");
        assertArrayEquals(assertion.toArray(), merged.toArray());
    }
}
