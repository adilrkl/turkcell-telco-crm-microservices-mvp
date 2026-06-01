package com.turkcell.identityservice.entity;

import jakarta.persistence.*;
import lombok.*;
 
import java.time.Instant;
import java.util.UUID;
 
@Entity
@Table(name = "refresh_tokens")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {
 
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
 
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
 
    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;
 
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
 
    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;
 
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
 
    public boolean isValid() {
        return !revoked && !isExpired();
    }
}
