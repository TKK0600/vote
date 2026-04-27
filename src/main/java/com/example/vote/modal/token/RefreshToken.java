package com.example.vote.modal.token;

import com.example.vote.modal.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Data
@Table(name = "refresh_token")
public class RefreshToken {
    @Id
    @GeneratedValue
    private Long id;

    private String token;

    @ManyToOne
    private User user;

    private Instant expiryDate;

    private String status;
}
