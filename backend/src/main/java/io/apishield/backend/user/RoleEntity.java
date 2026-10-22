package io.apishield.backend.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity @Table(name = "roles")
public class RoleEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, unique = true, length = 32) private RoleName name;
}
