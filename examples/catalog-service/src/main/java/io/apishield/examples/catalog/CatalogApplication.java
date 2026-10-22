package io.apishield.examples.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** Demo upstream #1: a small real product catalog (in memory). Reached through the gateway at /gateway/catalog/products. */
@SpringBootApplication
@RestController
public class CatalogApplication {
    public record Product(int id, String name, String category, double price) {}
    private static final List<Product> PRODUCTS = List.of(
            new Product(1, "Titanium Keyboard", "peripherals", 149.0), new Product(2, "Ultrawide Monitor", "displays", 599.0),
            new Product(3, "USB-C Dock", "peripherals", 89.5), new Product(4, "Noise-cancelling Headset", "audio", 219.0),
            new Product(5, "Standing Desk", "furniture", 449.0));

    public static void main(String[] args) { SpringApplication.run(CatalogApplication.class, args); }

    @GetMapping("/products") List<Product> products(@RequestParam(required = false) String category, @RequestParam(defaultValue = "50") int limit) {
        return PRODUCTS.stream().filter(p -> category == null || p.category().equalsIgnoreCase(category)).limit(Math.max(1, Math.min(limit, 100))).toList();
    }
    @GetMapping("/products/{id}") Product product(@PathVariable int id) {
        return PRODUCTS.stream().filter(p -> p.id() == id).findFirst().orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
    }
    /** Mutating endpoint so WRITE scopes and method restrictions can be demonstrated. */
    @PostMapping("/products") @ResponseStatus(org.springframework.http.HttpStatus.CREATED) Map<String, Object> create(@RequestBody Map<String, Object> body) {
        return Map.of("created", true, "echo", body);
    }
    /** Sleeps so the gateway's response timeout can be demonstrated. */
    @GetMapping("/slow") Map<String, String> slow(@RequestParam(defaultValue = "5000") long ms) throws InterruptedException {
        Thread.sleep(Math.min(ms, 30000)); return Map.of("status", "finally");
    }
    @GetMapping("/whoami") Map<String, String> whoami(@RequestHeader Map<String, String> headers) {
        Map<String, String> m = new TreeMap<>(); headers.forEach((k, v) -> m.put(k.toLowerCase(), v)); return m; // shows exactly which headers survived the gateway
    }
}
