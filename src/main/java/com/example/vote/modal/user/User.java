package com.example.vote.modal.user;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "users")
public class User {

//    public enum OnlineStatus {
//        ONLINE,
//        OFFLINE,
//        AWAY,
//        DO_NOT_DISTURB
//    }

//    public enum AuthProvider {
//        LOCAL,
//        GOOGLE,
//        FACEBOOK
//    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String email;

    private String userName;

    private String role;

    private LocalDateTime createdDate;

    // @Column(length = 4)
//    private int tag;

//    @Enumerated(EnumType.STRING)
//    private AuthProvider authProvider;



//    private LocalDateTime lastSeenAt;
//
//    @Enumerated(EnumType.STRING)
//    private OnlineStatus onlineStatus;
//
//    private String googleId;
}
