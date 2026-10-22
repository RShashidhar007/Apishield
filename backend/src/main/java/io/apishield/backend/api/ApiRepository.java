package io.apishield.backend.api;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

public interface ApiRepository extends JpaRepository<ApiDefinition, Long>, JpaSpecificationExecutor<ApiDefinition> {
    List<ApiDefinition> findByStatus(ApiStatus status);
    boolean existsByGatewayPathAndStatusNot(String gatewayPath, ApiStatus status);
    boolean existsByGatewayPathAndStatusNotAndIdNot(String gatewayPath, ApiStatus status, Long id);
    List<ApiDefinition> findByIdIn(Collection<Long> ids);
}
