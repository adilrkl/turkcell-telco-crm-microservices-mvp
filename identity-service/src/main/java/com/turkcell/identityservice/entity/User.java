package com.turkcell.identityservice.entity;

import jakarta.persistence.*;
import lombok.*;
 
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
 
@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
 
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
 
    @Column(nullable = false, unique = true, length = 100)
    private String username;
 
    @Column(nullable = false, unique = true, length = 255)
    private String email;
 
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
 
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;
 
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
 
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();
}
