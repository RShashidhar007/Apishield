package io.apishield.backend.gateway;

import io.apishield.common.model.GatewayConfig;
import io.apishield.common.model.IngestBatch;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Gateway -> management channel. Protected by the shared gateway token (see GatewayTokenFilter). Not exposed through the public ingress. */
@RestController
@RequestMapping("/internal/v1/gateway")
public class GatewayInternalController {
    private final GatewayConfigService config; private final IngestService ingest;
    public GatewayInternalController(GatewayConfigService config, IngestService ingest) { this.config = config; this.ingest = ingest; }

    @GetMapping("/config") GatewayConfig config() { return config.build(); }

    @PostMapping("/ingest") @ResponseStatus(HttpStatus.ACCEPTED)
    Map<String, Integer> ingest(@RequestBody IngestBatch batch) { return Map.of("accepted", ingest.ingest(batch)); }
}
