package com.brogrammers.open_mic_hub_service.user_management.user.entity;

import com.brogrammers.open_mic_hub_service.booking.entity.Booking;
import com.brogrammers.open_mic_hub_service.common.Auditable;
import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.Roles;
import com.brogrammers.open_mic_hub_service.security.oauth.AuthProvider;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name="users")
public class UserEntity extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, name = "full_name")
    private String fullName;

    @Column(nullable = false, name = "email", unique = true)
    private String emailId;

    @Column(name = "profile")
    private String profileImage;

    @ManyToMany(fetch = FetchType.EAGER)
    private List<Roles> roles = new ArrayList<>();

    /**
     * Null for an account created through a social provider — there is no password to store.
     *
     * <p>A database check constraint keeps a LOCAL account from reaching that state.
     */
    @Column(name = "password")
    private String password;

    /** Who vouches for this account. LOCAL means it authenticates with the password above. */
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 32)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    /**
     * The provider's own id for this person, null for LOCAL accounts.
     *
     * <p>Accounts are matched on this rather than on the email address: providers let people
     * change their address, and matching on email means a changed one silently becomes a
     * different account.
     */
    @Column(name = "provider_id", length = 191)
    private String providerId;

    @Column(name = "phone_number", unique = true)
    private String phoneNumber;

    private String location;

    @Column(nullable = false, name = "is_verified")
    private boolean verified = false;

    @Column(nullable = false, name = "is_active")
    private boolean isActive = true;

    @OneToMany(mappedBy = "userId", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Booking> bookings = new ArrayList<>();
}