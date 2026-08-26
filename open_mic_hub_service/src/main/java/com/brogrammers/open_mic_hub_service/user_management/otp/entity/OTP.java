package com.brogrammers.open_mic_hub_service.user_management.otp.entity;

import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class OTP {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private UserEntity user;

    @Column(unique = true, nullable = false)
    private String otpValue;

    private LocalDateTime expiryTime;

    private Boolean isUsed;

    @Enumerated(EnumType.STRING)
    private OTPPurpose purpose;

    @PrePersist
    public void prePersist() {
        this.isUsed = false;
    }
}
