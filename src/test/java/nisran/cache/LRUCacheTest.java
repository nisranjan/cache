package nisran.cache;

import org.junit.jupiter.api.Test;

//import nisran.cache.LRUCache;

import static org.junit.jupiter.api.Assertions.*;

public class LRUCacheTest {

    @Test
    public void testBasicOperations() {
        LRUCache<Integer, String> cache = new LRUCache<>(3);
        
        // Test set and get
        cache.set(1, "One");
        assertEquals("One", cache.get(1));
        
        // Test updating existing key
        cache.set(1, "One Updated");
        assertEquals("One Updated", cache.get(1));
    }

    @Test
    public void testCapacityLimit() {
        LRUCache<Integer, String> cache = new LRUCache<>(2);
        
        cache.set(1, "One");
        cache.set(2, "Two");
        cache.set(3, "Three"); // Should evict key 1
        
        assertNull(cache.get(1));
        assertEquals("Two", cache.get(2));
        assertEquals("Three", cache.get(3));
    }

    @Test
    public void testLRUOrder() {
        LRUCache<Integer, String> cache = new LRUCache<>(3);
        
        cache.set(1, "One");
        cache.set(2, "Two");
        cache.set(3, "Three");
        
        // Access key 1 to make it most recently used
        cache.get(1);
        
        // Adding new key should evict key 2 (least recently used)
        cache.set(4, "Four");
        
        assertNull(cache.get(2));
        assertEquals("One", cache.get(1));
        assertEquals("Three", cache.get(3));
        assertEquals("Four", cache.get(4));
    }

    @Test
    public void testNullValues() {
        LRUCache<Integer, String> cache = new LRUCache<>(2);
        
        cache.set(1, null);
        assertNull(cache.get(1));
        
        cache.set(2, "Two");
        assertEquals("Two", cache.get(2));
    }

    @Test
    public void testNonExistentKey() {
        LRUCache<Integer, String> cache = new LRUCache<>(2);
        
        assertNull(cache.get(1));
        
        cache.set(1, "One");
        assertNull(cache.get(2));
    }

    @Test
    public void testMultipleUpdates() {
        LRUCache<Integer, String> cache = new LRUCache<>(2);
        
        cache.set(1, "One");
        cache.set(1, "One Updated");
        cache.set(1, "One Updated Again");
        
        assertEquals("One Updated Again", cache.get(1));
    }

    @Test
    public void testEmptyCache() {
        LRUCache<Integer, String> cache = new LRUCache<>(2);
        
        assertNull(cache.get(1));
        assertNull(cache.get(2));
    }

    @Test
    public void testDifferentTypes() {
        LRUCache<String, Integer> cache = new LRUCache<>(2);
        
        cache.set("one", 1);
        cache.set("two", 2);
        
        assertEquals(1, cache.get("one"));
        assertEquals(2, cache.get("two"));
    }
} 