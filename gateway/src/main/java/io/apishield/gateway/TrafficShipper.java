package io.apishield.gateway;

import io.apishield.common.model.IngestBatch;
import io.apishield.common.model.TrafficEvent;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Asynchronous, batched metrics shipping: requests only enqueue (O(1), never blocks). A scheduler posts batches to the management API.
 * Delivery is at-most-once/best-effort: if the queue is full events are dropped and counted; failed batches are re-queued once if there is room.
 */
@Component
public class TrafficShipper {
    private static final Logger log = LoggerFactory.getLogger(TrafficShipper.class);
    private final BlockingQueue<TrafficEvent> queue; private final GatewayProperties props; private final WebClient client;
    private final AtomicLong dropped = new AtomicLong();

    public TrafficShipper(GatewayProperties props, WebClient.Builder builder) {
        this.props = props;
        this.queue = new ArrayBlockingQueue<>(props.getIngest().getQueueCapacity());
        this.client = builder.baseUrl(props.getManagementUrl()).defaultHeader("X-Gateway-Token", props.getGatewayToken()).build();
    }

    public void offer(TrafficEvent e) { if (!queue.offer(e)) dropped.incrementAndGet(); }
    public long dropped() { return dropped.get(); }
    public int queued() { return queue.size(); }

    @Scheduled(fixedDelayString = "${gateway.ingest.flush-interval-ms:5000}")
    public void flush() {
        List<TrafficEvent> batch = new ArrayList<>(props.getIngest().getBatchSize());
        while (queue.drainTo(batch, props.getIngest().getBatchSize()) > 0) {
            try {
                client.post().uri("/internal/v1/gateway/ingest").bodyValue(new IngestBatch(props.getInstanceId(), batch)).retrieve().toBodilessEntity().timeout(Duration.ofSeconds(5)).block();
            } catch (Exception e) {
                int lost = 0;
                for (TrafficEvent ev : batch) if (!queue.offer(ev)) lost++;
                if (lost > 0) dropped.addAndGet(lost);
                log.warn("Traffic ingest failed ({}); {} events re-queued, {} dropped", e.getClass().getSimpleName(), batch.size() - lost, lost);
                return;
            }
            batch = new ArrayList<>(props.getIngest().getBatchSize());
        }
        if (dropped.get() > 0 && log.isWarnEnabled()) log.warn("Total dropped traffic events: {}", dropped.get());
    }

    @PreDestroy void shutdown() { flush(); }
}
