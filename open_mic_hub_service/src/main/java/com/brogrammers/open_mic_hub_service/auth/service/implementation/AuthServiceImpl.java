package com.brogrammers.open_mic_hub_service.auth.service.implementation;

import com.brogrammers.open_mic_hub_service.auth.dto.TokenType;
import com.brogrammers.open_mic_hub_service.auth.dto.request.artist.ArtistRegistrationRequest;
import com.brogrammers.open_mic_hub_service.auth.dto.request.artist.ArtistGenreRequest;
import com.brogrammers.open_mic_hub_service.auth.dto.request.login.AuthRequest;
import com.brogrammers.open_mic_hub_service.auth.dto.request.user.UserRegistrationRequest;
import com.brogrammers.open_mic_hub_service.auth.dto.request.forgot_password.ResetPasswordRequest;
import com.brogrammers.open_mic_hub_service.auth.dto.response.ArtistRegistrationResponse;
import com.brogrammers.open_mic_hub_service.auth.dto.response.AuthResponse;
import com.brogrammers.open_mic_hub_service.auth.dto.response.ForgotPasswordResponse;
import com.brogrammers.open_mic_hub_service.auth.dto.response.UserRegistrationResponse;
import com.brogrammers.open_mic_hub_service.auth.messages.AuthExceptionMessages;
import com.brogrammers.open_mic_hub_service.auth.messages.AuthLogMessages;
import com.brogrammers.open_mic_hub_service.auth.messages.AuthResponseMessages;
import com.brogrammers.open_mic_hub_service.auth.service.AuthService;
import com.brogrammers.open_mic_hub_service.mail.MailService;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.Slugs;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.entity.Artist;
import com.brogrammers.open_mic_hub_service.user_management.artist.artist.repository.ArtistRepository;
import com.brogrammers.open_mic_hub_service.user_management.genere.dto.response.GenreResponse;
import com.brogrammers.open_mic_hub_service.user_management.genere.entity.Category;
import com.brogrammers.open_mic_hub_service.user_management.genere.repository.CategoryRepository;
import com.brogrammers.open_mic_hub_service.user_management.genere.repository.GenreRepository;
import com.brogrammers.open_mic_hub_service.user_management.otp.entity.OTP;
import com.brogrammers.open_mic_hub_service.user_management.otp.entity.OTPPurpose;
import com.brogrammers.open_mic_hub_service.user_management.otp.service.OTPService;
import com.brogrammers.open_mic_hub_service.security.jwt_auth.JwtTokenGenerator;
import com.brogrammers.open_mic_hub_service.user_management.user.dto.response.RolesResponse;
import com.brogrammers.open_mic_hub_service.user_management.user.entity.UserEntity;
import com.brogrammers.open_mic_hub_service.user_management.user.repository.UserInfoRepository;
import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.Roles;
import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.UserRole;
import com.brogrammers.open_mic_hub_service.user_management.user.role.repository.RolesRepository;
import com.brogrammers.open_mic_hub_service.util.file.FileHandlerUtil;
import com.brogrammers.open_mic_hub_service.util.file.FileType;
import com.brogrammers.open_mic_hub_service.util.validator.EmailValidator;
import com.brogrammers.open_mic_hub_service.util.validator.PasswordValidator;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.entity.VirtualCoin;
import com.brogrammers.open_mic_hub_service.virtual_coin_system.virtual_coin.repository.VirtualCoinRepository;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserInfoRepository userInfoRepository;
    private final ArtistRepository artistRepository;
    private final GenreRepository genreRepository;
    private final CategoryRepository categoryRepository;
    private final JwtTokenGenerator jwtTokenGenerator;
    private final AuthenticationManager authenticationManager;
    private final FileHandlerUtil fileHandlerUtil;
    private final RolesRepository rolesRepository;
    private final OTPService otpService;
    private final MailService mailService;
    private final VirtualCoinRepository virtualCoinRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${frontend.domain}")
    private String frontEndUrl;
    @Value("${frontend.forgot_password}")
    private String forgotPasswordUrl;
    @Value("${frontend.verify_email}")
    private String verifyEmailUrl;

    @Override
    public AuthResponse getJwtTokensAfterAuthentication(AuthRequest authenticationRequest, HttpServletResponse response) {
        try {
            // Authenticate the user credentials
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            authenticationRequest.email(),
                            authenticationRequest.password()
                    )
            );

            // Fetch user information after successful authentication
            var userInfoEntity = userInfoRepository.findByEmailId(authenticationRequest.email())
                    .orElseThrow(() -> {
                        log.error(AuthLogMessages.USER_NOT_FOUND, authenticationRequest.email());
                        return new ResponseStatusException(HttpStatus.NOT_FOUND, "USER NOT FOUND");                    });

            // Generate JWT tokens
            String accessToken = jwtTokenGenerator.generateAccessToken(authentication);

            log.info(AuthLogMessages.ACCESS_TOKEN_GENERATED, userInfoEntity.getEmailId());

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .accessTokenExpiry(15 * 60)
                    .userRole(userInfoEntity.getRoles().stream().map(RolesResponse::new).toList())
                    .tokenType(TokenType.Bearer)
                    .build();

        } catch (AuthenticationException e) {
            log.error(AuthLogMessages.INVALID_CREDENTIALS, authenticationRequest.email());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, AuthExceptionMessages.INVALID_CREDENTIALS);
        } catch (Exception e) {
            log.error(AuthLogMessages.EXCEPTION_AUTHENTICATING, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, AuthExceptionMessages.TRY_AGAIN);
        }
    }




    /**
     * Sends a reset link if the address belongs to an account.
     *
     * <p>The response is the same either way: answering 404 for an unknown address turned this
     * endpoint into a way to enumerate who has an account.
     */
    @Override
    public ForgotPasswordResponse forgotPassword(String email) {
        var user = userInfoRepository.findByEmailId(email);
        if (user.isPresent()) {
            OTP otp = otpService.saveOTP(user.get(), OTPPurpose.FORGOT_PASSWORD);
            String forgotPasswordLink = frontEndUrl + forgotPasswordUrl + "?token=" + otp.getOtpValue();
            mailService.sendForgotPasswordMail(user.get(), forgotPasswordLink, otp.getExpiryTime());
        } else {
            log.info("Password reset requested for unknown address; no mail sent.");
        }
        return new ForgotPasswordResponse(AuthResponseMessages.PASSWORD_RESET_LINK_SENT, email, null);
    }

    /**
     * Redeems a password-reset token.
     *
     * <p>The token is single-use: it is consumed here, so replaying the same link fails even inside
     * its validity window.
     */
    @Override
    @Transactional
    public String resetPassword(ResetPasswordRequest resetPasswordRequest) {
        if (!PasswordValidator.isValid(resetPasswordRequest.getNewPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at least 8 characters and include an uppercase letter, "
                            + "a lowercase letter, a number and a special character.");
        }

        OTP otp = otpService.getOTP(resetPasswordRequest.getOtp(), OTPPurpose.FORGOT_PASSWORD);
        UserEntity userEntity = otp.getUser();
        userEntity.setPassword(passwordEncoder.encode(resetPasswordRequest.getNewPassword()));
        userInfoRepository.save(userEntity);
        otpService.consumeOTP(otp);

        log.info("Password reset completed for {}", userEntity.getEmailId());
        return AuthResponseMessages.PASSWORD_RESET_SUCCESS;
    }

    @Override
    @Transactional
    public UserRegistrationResponse registerUser(UserRegistrationRequest registration) {
        if (!EmailValidator.isValid(registration.userEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AuthExceptionMessages.INVALID_EMAIL);
        }

        UserEntity userEntity = new UserEntity();
        userEntity.setFullName(registration.fullName());
        userEntity.setEmailId(registration.userEmail());
        userEntity.setPassword(passwordEncoder.encode(registration.password()));
        userEntity.setPhoneNumber(registration.phoneNumber());
        userEntity.setLocation(registration.location());
        // Self-registration creates an ORGANIZER: on this platform the person signing up is the
        // one who books and pays for artists. USER exists as a lower audience tier that staff can
        // assign, but nothing self-registers into it.
        Roles role = rolesRepository.findByName(UserRole.ORGANIZER.name())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
        userEntity.getRoles().add(role);
        userEntity.setVerified(false);
        OTP otp = otpService.saveOTP(userEntity, OTPPurpose.REGISTER);
        String verifyUrl = this.frontEndUrl + verifyEmailUrl + "?email=" + registration.userEmail();
        URI frontEndUri = URI.create(verifyUrl);
        mailService.sendRegistrationMail(userEntity, otp, frontEndUri);
        if (registration.profileImage() == null || registration.profileImage().isEmpty()) {
            return new UserRegistrationResponse(userInfoRepository.save(userEntity), otp.getExpiryTime());
        }

        FileType fileType = fileHandlerUtil.determineFileType(Objects.requireNonNull(registration.profileImage().getOriginalFilename()));
        if (fileType == FileType.IMAGE) {
            String fileName = fileHandlerUtil.saveFile(registration.profileImage(), registration.userEmail()).getFileDownloadUri();
            userEntity.setProfileImage(fileName);
            userInfoRepository.save(userEntity);
            return new UserRegistrationResponse(userInfoRepository.save(userEntity), otp.getExpiryTime());
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AuthExceptionMessages.INVALID_FILE_TYPE);
    }

    @Override
    @Transactional
    public ArtistRegistrationResponse registerArtist(ArtistRegistrationRequest registration) {
        if (!EmailValidator.isValid(registration.getUserEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AuthExceptionMessages.INVALID_EMAIL);
        }

        // Create UserEntity
        UserEntity userEntity = new UserEntity();
        userEntity.setFullName(registration.getFullName());
        userEntity.setEmailId(registration.getUserEmail());
        userEntity.setPassword(passwordEncoder.encode(registration.getPassword()));
        userEntity.setPhoneNumber(registration.getPhoneNumber());
        userEntity.setLocation(registration.getLocation());
        userEntity.setVerified(false);

        // Assign ARTIST role
        Roles artistRole = rolesRepository.findByName(UserRole.ARTIST.name())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
        userEntity.getRoles().add(artistRole);

        // Handle profile image
        if (registration.getProfileImage() != null && !registration.getProfileImage().isEmpty()) {
            FileType fileType = fileHandlerUtil.determineFileType(
                    Objects.requireNonNull(registration.getProfileImage().getOriginalFilename()));
            if (fileType == FileType.IMAGE) {
                String fileName = fileHandlerUtil.saveFile(registration.getProfileImage(), registration.getUserEmail()).getFileDownloadUri();
                userEntity.setProfileImage(fileName);
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AuthExceptionMessages.INVALID_FILE_TYPE);
            }
        }

        userInfoRepository.save(userEntity);

        // Validate genres structure to prevent binding exceptions
        if (registration.getGenres() == null || registration.getGenres().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AuthExceptionMessages.INVALID_GENRE_DATA);
        }
        for (ArtistGenreRequest genreReq : registration.getGenres()) {
            if (genreReq == null || genreReq.getSubGenreIds() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AuthExceptionMessages.INVALID_GENRE_DATA);
            }
        }

        // Fetch selected categories (sub-genres) for the artist
        List<Long> categoryIds = registration.getGenres().stream()
                .flatMap(genreReq -> genreReq.getSubGenreIds().stream())
                .toList();
        List<Category> selectedCategories = categoryRepository.findAllById(categoryIds);

        // Create and save Artist entity with genres/categories
        var artist = new Artist();
        artist.setUser(userEntity);
        artist.setStageName(registration.getStageName());
        // Assigned once, at creation, and never rewritten: a public link that has been shared
        // should keep working even if the artist renames themselves later.
        artist.setSlug(Slugs.uniqueSlug(registration.getStageName(), artistRepository::existsBySlug));
        artist.setBio(registration.getBio());
        artist.setGenres(selectedCategories);
        Artist savedArtist = artistRepository.save(artist);
        VirtualCoin virtualCoin = new VirtualCoin();
        virtualCoin.setArtist(savedArtist);
        virtualCoin.setBalance(0.0);
        virtualCoinRepository.save(virtualCoin);

        // Group selected categories by their parent genre
        Map<Long, List<Category>> genreIdToCategories = registration.getGenres().stream()
                .collect(java.util.stream.Collectors.toMap(
                        ArtistGenreRequest::getGenreId,
                        genreReq -> selectedCategories.stream()
                                .filter(cat -> genreReq.getSubGenreIds().contains(cat.getId()))
                                .toList()
                ));

        // Build GenreResponse list
        List<GenreResponse> genreResponses = genreIdToCategories.entrySet().stream()
                .map(entry -> {
                    var genreOpt = genreRepository.findById(entry.getKey());
                    if (genreOpt.isEmpty()) return null;
                    var genre = genreOpt.get();
                    var categoryResponses = entry.getValue().stream()
                            .map(cat -> new com.brogrammers.open_mic_hub_service.user_management.genere.dto.response.CategoryResponse(
                                    cat.getId(), cat.getName(), cat.getDescription(), cat.isActive()))
                            .toList();
                    return new GenreResponse(
                            genre.getId(), genre.getName(), genre.getDescription(), genre.getSlug(), categoryResponses
                    );
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        // Generate OTP and send verification email
        OTP otp = otpService.saveOTP(userEntity, OTPPurpose.REGISTER);
        String verifyUrl = this.frontEndUrl + verifyEmailUrl + "?email=" + registration.getUserEmail();
        URI frontEndUri = URI.create(verifyUrl);
        mailService.sendRegistrationMail(userEntity, otp, frontEndUri);

        ArtistRegistrationResponse response = new ArtistRegistrationResponse(artist, otp.getExpiryTime());
        response.setGenre(genreResponses);
        return response;
    }

    @Override
    public String verifyEmail(String email, String token) {
        if (!EmailValidator.isValid(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AuthExceptionMessages.INVALID_EMAIL);
        }

        UserEntity user = userInfoRepository.findByEmailId(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, AuthExceptionMessages.USER_NOT_FOUND + email));

        if (user.isVerified()) {
            return AuthResponseMessages.EMAIL_ALREADY_VERIFIED;
        }

        OTP otp = otpService.getOTP(token, OTPPurpose.REGISTER);
        if (!otp.getOtpValue().equals(token)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AuthExceptionMessages.INVALID_OTP);
        }
        otpService.validateOTP(user,token, OTPPurpose.REGISTER);

        user.setVerified(true);
        userInfoRepository.save(user);
        return AuthResponseMessages.EMAIL_VERIFIED;
    }

    @Override
    public UserRegistrationResponse resendVerificationEmail(String email) {
        if (!EmailValidator.isValid(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AuthExceptionMessages.INVALID_EMAIL);
        }

        UserEntity user = userInfoRepository.findByEmailId(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, AuthExceptionMessages.USER_NOT_FOUND + email));

        if (user.isVerified()) {
            return new UserRegistrationResponse(user, null);
        }

        OTP otp = otpService.saveOTP(user, OTPPurpose.REGISTER);
        String verifyUrl = this.frontEndUrl + verifyEmailUrl + "?email=" + email;
        URI frontEndUri = URI.create(verifyUrl);
        mailService.sendRegistrationMail(user, otp, frontEndUri);

        return new UserRegistrationResponse(userInfoRepository.save(user), otp.getExpiryTime());
    }

}