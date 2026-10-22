package io.apishield.backend.config;

import io.apishield.backend.analytics.RequestLogRepository;
import io.apishield.backend.auth.RefreshTokenRepository;
import io.apishield.backend.threat.SecurityEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

/** Daily retention. Audit logs are intentionally never purged automatically (see docs/DATABASE_DESIGN.md). */
@Component
public class RetentionJob {
    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);
    private final RequestLogRepository logs; private final SecurityEventRepository events; private final RefreshTokenRepository tokens;
    private final AppProperties props; private final TransactionTemplate tx;

    public RetentionJob(RequestLogRepository logs, SecurityEventRepository events, RefreshTokenRepository tokens, AppProperties props, TransactionTemplate tx) {
        this.logs = logs; this.events = events; this.tokens = tokens; this.props = props; this.tx = tx;
    }

    @Scheduled(cron = "${apishield.retention.cron:0 15 3 * * *}")
    public void purge() {
        Instant logCutoff = Instant.now().minus(Duration.ofDays(props.getRetention().getRequestLogDays()));
        int total = 0, n;
        do { n = tx.execute(s -> logs.purgeBatch(logCutoff)); total += n; } while (n > 0);
        int ev = tx.execute(s -> events.purge(Instant.now().minus(Duration.ofDays(props.getRetention().getSecurityEventDays()))));
        int rt = tx.execute(s -> tokens.deleteExpired(Instant.now().minus(Duration.ofDays(1))));
        log.info("Retention purge: requestLogs={}, securityEvents={}, refreshTokens={}", total, ev, rt);
    }
}
