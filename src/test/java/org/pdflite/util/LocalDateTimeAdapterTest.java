package org.pdflite.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LocalDateTimeAdapter.
 */
class LocalDateTimeAdapterTest {

    private LocalDateTimeAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LocalDateTimeAdapter();
    }

    @Test
    void testSerialize() {
        LocalDateTime dateTime = LocalDateTime.of(2025, 12, 21, 15, 30, 45);
        
        JsonElement result = adapter.serialize(dateTime, LocalDateTime.class, null);
        
        assertNotNull(result);
        assertTrue(result.isJsonPrimitive());
        assertEquals("2025-12-21T15:30:45", result.getAsString());
    }

    @Test
    void testDeserialize() {
        JsonElement json = new JsonPrimitive("2025-12-21T15:30:45");
        
        LocalDateTime result = adapter.deserialize(json, LocalDateTime.class, null);
        
        assertNotNull(result);
        assertEquals(2025, result.getYear());
        assertEquals(12, result.getMonthValue());
        assertEquals(21, result.getDayOfMonth());
        assertEquals(15, result.getHour());
        assertEquals(30, result.getMinute());
        assertEquals(45, result.getSecond());
    }

    @Test
    void testSerializeDeserializeRoundTrip() {
        LocalDateTime original = LocalDateTime.of(2025, 1, 1, 0, 0, 0);
        
        JsonElement serialized = adapter.serialize(original, LocalDateTime.class, null);
        LocalDateTime deserialized = adapter.deserialize(serialized, LocalDateTime.class, null);
        
        assertEquals(original, deserialized);
    }

    @Test
    void testDeserializeInvalidFormat() {
        JsonElement json = new JsonPrimitive("invalid-date");
        
        assertThrows(Exception.class, () -> 
            adapter.deserialize(json, LocalDateTime.class, null)
        );
    }

    @Test
    void testSerializeWithNanoseconds() {
        LocalDateTime dateTime = LocalDateTime.of(2025, 12, 21, 15, 30, 45, 123456789);
        
        JsonElement result = adapter.serialize(dateTime, LocalDateTime.class, null);
        
        assertNotNull(result);
        assertTrue(result.getAsString().contains("2025-12-21T15:30:45"));
    }

    @Test
    void testDeserializeWithNanoseconds() {
        JsonElement json = new JsonPrimitive("2025-12-21T15:30:45.123456789");
        
        LocalDateTime result = adapter.deserialize(json, LocalDateTime.class, null);
        
        assertNotNull(result);
        assertEquals(123456789, result.getNano());
    }

    @Test
    void testSerializeMidnight() {
        LocalDateTime dateTime = LocalDateTime.of(2025, 12, 21, 0, 0, 0);
        
        JsonElement result = adapter.serialize(dateTime, LocalDateTime.class, null);
        
        assertEquals("2025-12-21T00:00:00", result.getAsString());
    }

    @Test
    void testSerializeEndOfDay() {
        LocalDateTime dateTime = LocalDateTime.of(2025, 12, 21, 23, 59, 59);
        
        JsonElement result = adapter.serialize(dateTime, LocalDateTime.class, null);
        
        assertEquals("2025-12-21T23:59:59", result.getAsString());
    }
}
