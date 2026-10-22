package io.apishield.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserAccount, Long>, JpaSpecificationExecutor<UserAccount> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    @org.springframework.data.jpa.repository.Query("select count(u) from UserAccount u join u.roles r where r.name = :role and u.active = true")
    long countActiveWithRole(RoleName role);
}
