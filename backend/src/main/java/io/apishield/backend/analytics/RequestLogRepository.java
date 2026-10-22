package io.apishield.backend.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Native queries use T-SQL. apiId = 0 means "all APIs" (avoids typed-null parameter problems on SQL Server). */
public interface RequestLogRepository extends JpaRepository<RequestLog, Long>, JpaSpecificationExecutor<RequestLog> {

    @Query(value = """
        select count(*),
          coalesce(sum(case when status < 400 then 1 else 0 end),0),
          coalesce(sum(case when status >= 400 then 1 else 0 end),0),
          coalesce(avg(cast(latency_ms as float)),0),
          coalesce(sum(case when outcome = 'RATE_LIMITED' then 1 else 0 end),0),
          coalesce(sum(case when outcome = 'AUTH_FAILED' then 1 else 0 end),0),
          coalesce(sum(case when outcome in ('FORBIDDEN','METHOD_NOT_ALLOWED','PAYLOAD_TOO_LARGE','DESTINATION_BLOCKED') then 1 else 0 end),0),
          coalesce(sum(case when outcome in ('UPSTREAM_ERROR','UPSTREAM_TIMEOUT','UPSTREAM_UNAVAILABLE') then 1 else 0 end),0),
          coalesce(avg(cast(upstream_latency_ms as float)),0)
        from request_logs where ts >= :fromTs and ts < :toTs and (:apiId = 0 or api_id = :apiId)""", nativeQuery = true)
    List<Object[]> summary(Instant fromTs, Instant toTs, long apiId);

    @Query(value = """
        select top 1 percentile_cont(0.95) within group (order by latency_ms) over ()
        from request_logs where ts >= :fromTs and ts < :toTs and (:apiId = 0 or api_id = :apiId)""", nativeQuery = true)
    List<Object> p95(Instant fromTs, Instant toTs, long apiId);

    @Query(value = """
        select dateadd(minute, (datediff(minute, 0, ts) / :bucket) * :bucket, 0) as b,
          count(*), sum(case when status >= 400 then 1 else 0 end), avg(cast(latency_ms as float)), max(latency_ms)
        from request_logs where ts >= :fromTs and ts < :toTs and (:apiId = 0 or api_id = :apiId)
        group by dateadd(minute, (datediff(minute, 0, ts) / :bucket) * :bucket, 0) order by 1""", nativeQuery = true)
    List<Object[]> timeseries(Instant fromTs, Instant toTs, long apiId, int bucket);

    @Query(value = """
        select r.api_id, a.name, a.version, count(*), sum(case when r.status >= 400 then 1 else 0 end), avg(cast(r.latency_ms as float)), max(r.ts)
        from request_logs r left join apis a on a.id = r.api_id
        where r.ts >= :fromTs and r.ts < :toTs
        group by r.api_id, a.name, a.version order by count(*) desc""", nativeQuery = true)
    List<Object[]> byApi(Instant fromTs, Instant toTs);

    @Query(value = "select status, count(*) from request_logs where ts >= :fromTs and ts < :toTs and (:apiId = 0 or api_id = :apiId) group by status order by status", nativeQuery = true)
    List<Object[]> statusDistribution(Instant fromTs, Instant toTs, long apiId);

    @Query(value = "select method, count(*) from request_logs where ts >= :fromTs and ts < :toTs and (:apiId = 0 or api_id = :apiId) group by method order by count(*) desc", nativeQuery = true)
    List<Object[]> methodDistribution(Instant fromTs, Instant toTs, long apiId);

    @Query(value = "select key_id, count(*) from request_logs where key_id in (:ids) and ts >= :since group by key_id", nativeQuery = true)
    List<Object[]> usageByKey(Collection<Long> ids, Instant since);

    @Query(value = """
        select count(*) from request_logs where api_id = :apiId and ts >= :since and outcome <> 'RATE_LIMITED'
          and (:keyId = 0 or key_id = :keyId)""", nativeQuery = true)
    long countSince(long apiId, long keyId, Instant since);

    long countByClientIpAndOutcomeAndTsAfter(String clientIp, String outcome, Instant since);
    long countByClientIpAndOutcomeInAndTsAfter(String clientIp, Collection<String> outcomes, Instant since);

    @Modifying
    @Query(value = "delete top (5000) from request_logs where ts < :cutoff", nativeQuery = true)
    int purgeBatch(Instant cutoff);
}
