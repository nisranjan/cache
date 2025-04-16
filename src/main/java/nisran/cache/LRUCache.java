package nisran.cache;

import java.util.HashMap;
import java.util.LinkedList;

public class LRUCache<K, V> {
    private final int capacity;
    protected final LinkedList<K> list;
    protected final HashMap<K, V> dict;

    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.list = new LinkedList<>();
        this.dict = new HashMap<>();
    }

    public V get(K key) {
        if (!dict.containsKey(key)) {
            return null;
        }
        
        // Move the key to the front of the list (most recently used)
        list.remove(key);
        list.addFirst(key);
        
        return dict.get(key);
    }

    public void set(K key, V value) {
        if (dict.containsKey(key)) {
            // Update existing key
            list.remove(key);
        } else if (dict.size() >= capacity) {
            // Remove least recently used element
            K lruKey = list.removeLast();
            dict.remove(lruKey);
        }
        
        // Add new key-value pair
        list.addFirst(key);
        dict.put(key, value);
    }
} 