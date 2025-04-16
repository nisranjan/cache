package nisran.controller;

import nisran.cache.LRUCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cache")
public class CacheController {

    private final LRUCache<String, Object> cache;

    @Autowired
    public CacheController(LRUCache<String, Object> cache) {
        this.cache = cache;
    }

    @GetMapping("/{key}")
    public ResponseEntity<Object> get(@PathVariable String key) {
        Object value = cache.get(key);
        if (value == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(value);
    }

    @PostMapping("/{key}")
    public ResponseEntity<Void> set(@PathVariable String key, @RequestBody Object value) {
        cache.set(key, value);
        return ResponseEntity.ok().build();
    }
} 