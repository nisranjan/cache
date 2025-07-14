package nisran.controller;

import nisran.router.QuorumRWService;
import nisran.router.QuorumReaderWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


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

    @GetMapping("/local/{key}")
    public ResponseEntity<Object> getLocal(@PathVariable String key) {
        Object value = ((QuorumReaderWriter)quorumRWService).localRead(key); //Typecasted
        if (value == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(value);
    }

    @PostMapping("/{key}")
    public ResponseEntity<List<String>> set(@PathVariable String key, @RequestBody Object value) {
        logger.debug("Called Set() key: {}, value: {}", key, value);
        List<String> response = quorumRWService.quorumWrite(key, value);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/local/{key}")
    public ResponseEntity<Void> setLocal(@PathVariable String key, @RequestBody Object value) {
        logger.debug("Called Set() key: {}, value: {}", key, value);
        ((QuorumReaderWriter)quorumRWService).localWrite(key, value);
        return ResponseEntity.ok().build();
    }
    
}