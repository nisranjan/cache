package nisran.controller;

import nisran.router.QuorumRWService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("cluster") // Active when 'cluster' profile is active
@RequestMapping("/api/cache")
public class RoutingCacheController {

    private static final Logger logger = LoggerFactory.getLogger(RoutingCacheController.class);

    private final QuorumRWService quorumRWService;

    @Autowired
    public RoutingCacheController(QuorumRWService quorumRWService) {
        this.quorumRWService = quorumRWService;
    }

    @GetMapping("/{key}")
    public ResponseEntity<Object> get(@PathVariable String key) {
        Object value = quorumRWService.quorumRead(key);
        if (value == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(value);
    }

    @PostMapping("/{key}")
    public ResponseEntity<Void> set(@PathVariable String key, @RequestBody Object value) {
        logger.debug("Called Set() key: {}, value: {}", key, value);
        quorumRWService.quorumWrite(key, value);
        return ResponseEntity.ok().build();
    }
    
}