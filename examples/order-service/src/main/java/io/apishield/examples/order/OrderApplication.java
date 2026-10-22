package io.apishield.examples.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Demo upstream #2: order management (in memory). Reached through the gateway at /gateway/orders/orders. */
@SpringBootApplication
@RestController
public class OrderApplication {
    public record OrderRequest(String customer, List<Integer> productIds) {}
    public record Order(int id, String customer, List<Integer> productIds, String status) {}
    private final Map<Integer, Order> orders = new ConcurrentHashMap<>();
    private final AtomicInteger seq = new AtomicInteger(1000);

    public static void main(String[] args) { SpringApplication.run(OrderApplication.class, args); }

    @GetMapping("/orders") List<Order> list() { return List.copyOf(orders.values()); }
    @GetMapping("/orders/{id}") Order get(@PathVariable int id) {
        Order o = orders.get(id); if (o == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND); return o;
    }
    @PostMapping("/orders") @ResponseStatus(HttpStatus.CREATED) Order create(@RequestBody OrderRequest r) {
        if (r.customer() == null || r.customer().isBlank() || r.productIds() == null || r.productIds().isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customer and productIds are required");
        Order o = new Order(seq.incrementAndGet(), r.customer(), r.productIds(), "PLACED"); orders.put(o.id(), o); return o;
    }
}
