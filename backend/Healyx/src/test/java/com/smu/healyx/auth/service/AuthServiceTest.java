package com.smu.healyx.auth.service;

import com.smu.healyx.auth.dto.*;
import com.smu.healyx.common.exception.AuthException;
import com.smu.healyx.common.security.JwtProvider;
import com.smu.healyx.email.service.EmailService;
import com.smu.healyx.user.domain.User;
import com.smu.healyx.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtProvider jwtProvider;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private EmailService emailService;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        // Register 등 Redis 미사용 테스트에서도 실패하지 않도록 lenient 처리
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ─────────────────────────────────────────────
    // 공통 헬퍼
    // ─────────────────────────────────────────────

    private User buildUser() {
        return User.builder()
                .userId(1L)
                .username("testuser")
                .passwordHash("$2a$10$hashedPassword")
                .realName("홍길동")
                .email("test@example.com")
                .nickname("테스터")
                .gender("M")
                .birthDate(LocalDate.of(1995, 6, 15))
                .age(29)
                .hasHealthInsurance(true)
                .preferredLanguage("ko")
                .pushEnabled(true)
                .loginFailedCount(0)
                .isActive(true)
                .build();
    }

    // ─────────────────────────────────────────────
    // 1. 회원가입
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("회원가입")
    class Register {

        @Test
        @DisplayName("정상 입력 시 사용자가 저장된다")
        void register_success() {
            RegisterRequest req = mock(RegisterRequest.class);
            given(req.getUsername()).willReturn("testuser");
            given(req.getEmail()).willReturn("test@example.com");
            given(req.getRealName()).willReturn("홍길동");
            given(req.getNickname()).willReturn("테스터");
            given(req.getPassword()).willReturn("Test123!");
            given(req.getBirthDate()).willReturn(LocalDate.of(1995, 6, 15));
            given(req.getPreferredLanguage()).willReturn("ko");
            given(userRepository.existsByUsername("testuser")).willReturn(false);
            given(userRepository.existsByEmail("test@example.com")).willReturn(false);
            given(passwordEncoder.encode("Test123!")).willReturn("$2a$10$hashed");

            authService.register(req);

            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("아이디 중복 시 USERNAME_DUPLICATE(409) 예외가 발생한다")
        void register_duplicateUsername_throwsConflict() {
            RegisterRequest req = mock(RegisterRequest.class);
            given(req.getUsername()).willReturn("testuser");
            given(userRepository.existsByUsername("testuser")).willReturn(true);

            assertThatThrownBy(() -> authService.register(req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> {
                        AuthException ae = (AuthException) e;
                        assertThat(ae.getErrorCode()).isEqualTo("USERNAME_DUPLICATE");
                        assertThat(ae.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    });
        }

        @Test
        @DisplayName("이메일 중복 시 EMAIL_DUPLICATE(409) 예외가 발생한다")
        void register_duplicateEmail_throwsConflict() {
            RegisterRequest req = mock(RegisterRequest.class);
            given(req.getUsername()).willReturn("testuser");
            given(req.getEmail()).willReturn("test@example.com");
            given(userRepository.existsByUsername("testuser")).willReturn(false);
            given(userRepository.existsByEmail("test@example.com")).willReturn(true);

            assertThatThrownBy(() -> authService.register(req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> {
                        AuthException ae = (AuthException) e;
                        assertThat(ae.getErrorCode()).isEqualTo("EMAIL_DUPLICATE");
                        assertThat(ae.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    });
        }
    }

    // ─────────────────────────────────────────────
    // 2. 로그인
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("로그인")
    class Login {

        @Test
        @DisplayName("정상 로그인 시 토큰과 프로필 필드가 모두 반환된다")
        void login_success_returnsTokenAndProfile() {
            User user = buildUser();
            LoginRequest req = mock(LoginRequest.class);
            given(req.getUsername()).willReturn("testuser");
            given(req.getPassword()).willReturn("Test123!");
            given(userRepository.findByUsername("testuser")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("Test123!", user.getPasswordHash())).willReturn(true);
            given(jwtProvider.generateAccessToken(1L)).willReturn("access");
            given(jwtProvider.generateRefreshToken(1L)).willReturn("refresh");

            LoginResponse res = authService.login(req);

            assertThat(res.getAccessToken()).isEqualTo("access");
            assertThat(res.getRefreshToken()).isEqualTo("refresh");
            assertThat(res.getUserId()).isEqualTo(1L);
            assertThat(res.getUsername()).isEqualTo("testuser");
            assertThat(res.getNickname()).isEqualTo("테스터");
            assertThat(res.getName()).isEqualTo("홍길동");
            assertThat(res.getEmail()).isEqualTo("test@example.com");
            assertThat(res.isInsuranceStatus()).isTrue();
        }

        @Test
        @DisplayName("로그인 성공 시 Refresh Token이 Redis에 저장된다")
        void login_success_savesRefreshTokenInRedis() {
            User user = buildUser();
            LoginRequest req = mock(LoginRequest.class);
            given(req.getUsername()).willReturn("testuser");
            given(req.getPassword()).willReturn("Test123!");
            given(userRepository.findByUsername("testuser")).willReturn(Optional.of(user));
            given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);
            given(jwtProvider.generateAccessToken(1L)).willReturn("access");
            given(jwtProvider.generateRefreshToken(1L)).willReturn("refresh");

            authService.login(req);

            verify(valueOperations).set(
                    eq("user:1:refresh_token"),
                    eq("refresh"),
                    eq(7L),
                    eq(TimeUnit.DAYS)
            );
        }

        @Test
        @DisplayName("존재하지 않는 아이디로 로그인 시 UNAUTHORIZED 예외가 발생한다")
        void login_userNotFound_throwsUnauthorized() {
            LoginRequest req = mock(LoginRequest.class);
            given(req.getUsername()).willReturn("unknown");
            given(userRepository.findByUsername("unknown")).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> assertThat(((AuthException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        }

        @Test
        @DisplayName("잠금 중인 계정으로 로그인 시 ACCOUNT_LOCKED(403) 예외가 발생한다")
        void login_accountLocked_throwsForbidden() {
            User locked = User.builder()
                    .userId(1L).username("testuser").passwordHash("hash")
                    .realName("홍길동").email("test@example.com").nickname("테스터")
                    .preferredLanguage("ko").loginFailedCount(5)
                    .lockedUntil(LocalDateTime.now().plusMinutes(20))
                    .isActive(true).build();
            LoginRequest req = mock(LoginRequest.class);
            given(req.getUsername()).willReturn("testuser");
            given(userRepository.findByUsername("testuser")).willReturn(Optional.of(locked));

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> {
                        AuthException ae = (AuthException) e;
                        assertThat(ae.getErrorCode()).isEqualTo("ACCOUNT_LOCKED");
                        assertThat(ae.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    });
        }

        @Test
        @DisplayName("비밀번호 오류 시 loginFailedCount가 1 증가한다")
        void login_wrongPassword_incrementsFailCount() {
            User user = buildUser(); // loginFailedCount = 0
            LoginRequest req = mock(LoginRequest.class);
            given(req.getUsername()).willReturn("testuser");
            given(req.getPassword()).willReturn("wrong!");
            given(userRepository.findByUsername("testuser")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("wrong!", user.getPasswordHash())).willReturn(false);

            assertThatThrownBy(() -> authService.login(req)).isInstanceOf(AuthException.class);

            assertThat(user.getLoginFailedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("비밀번호 5회 오류 시 계정이 잠기고 lockedUntil이 설정된다")
        void login_fiveWrongPasswords_locksAccount() {
            User user = User.builder()
                    .userId(1L).username("testuser").passwordHash("hash")
                    .realName("홍길동").email("test@example.com").nickname("테스터")
                    .preferredLanguage("ko").loginFailedCount(4).isActive(true).build();
            LoginRequest req = mock(LoginRequest.class);
            given(req.getUsername()).willReturn("testuser");
            given(req.getPassword()).willReturn("wrong!");
            given(userRepository.findByUsername("testuser")).willReturn(Optional.of(user));
            given(passwordEncoder.matches(anyString(), anyString())).willReturn(false);

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> assertThat(((AuthException) e).getErrorCode()).isEqualTo("ACCOUNT_LOCKED"));

            assertThat(user.getLockedUntil()).isNotNull();
            assertThat(user.getLockedUntil()).isAfter(LocalDateTime.now());
        }

        @Test
        @DisplayName("로그인 성공 시 loginFailedCount와 lockedUntil이 초기화된다")
        void login_success_resetsFailCount() {
            User user = User.builder()
                    .userId(1L).username("testuser").passwordHash("hash")
                    .realName("홍길동").email("test@example.com").nickname("테스터")
                    .preferredLanguage("ko").loginFailedCount(3).isActive(true).build();
            LoginRequest req = mock(LoginRequest.class);
            given(req.getUsername()).willReturn("testuser");
            given(req.getPassword()).willReturn("Test123!");
            given(userRepository.findByUsername("testuser")).willReturn(Optional.of(user));
            given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);
            given(jwtProvider.generateAccessToken(1L)).willReturn("access");
            given(jwtProvider.generateRefreshToken(1L)).willReturn("refresh");

            authService.login(req);

            assertThat(user.getLoginFailedCount()).isEqualTo(0);
            assertThat(user.getLockedUntil()).isNull();
        }
    }

    // ─────────────────────────────────────────────
    // 3. Access Token 재발급
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("Access Token 재발급")
    class Refresh {

        @Test
        @DisplayName("유효한 Refresh Token으로 새 Access Token이 발급된다")
        void refresh_success() {
            TokenRefreshRequest req = mock(TokenRefreshRequest.class);
            given(req.getRefreshToken()).willReturn("validRefresh");
            given(jwtProvider.validateToken("validRefresh")).willReturn(true);
            given(jwtProvider.getUserId("validRefresh")).willReturn(1L);
            given(valueOperations.get("user:1:refresh_token")).willReturn("validRefresh");
            given(jwtProvider.generateAccessToken(1L)).willReturn("newAccess");

            TokenRefreshResponse res = authService.refresh(req);

            assertThat(res.getAccessToken()).isEqualTo("newAccess");
        }

        @Test
        @DisplayName("유효하지 않은 토큰이면 INVALID_REFRESH_TOKEN(401) 예외가 발생한다")
        void refresh_invalidToken_throwsUnauthorized() {
            TokenRefreshRequest req = mock(TokenRefreshRequest.class);
            given(req.getRefreshToken()).willReturn("invalidToken");
            given(jwtProvider.validateToken("invalidToken")).willReturn(false);

            assertThatThrownBy(() -> authService.refresh(req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> {
                        AuthException ae = (AuthException) e;
                        assertThat(ae.getErrorCode()).isEqualTo("INVALID_REFRESH_TOKEN");
                        assertThat(ae.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    });
        }

        @Test
        @DisplayName("Redis 저장 토큰과 불일치 시 REFRESH_TOKEN_MISMATCH(401) 예외가 발생한다")
        void refresh_tokenMismatch_throwsUnauthorized() {
            TokenRefreshRequest req = mock(TokenRefreshRequest.class);
            given(req.getRefreshToken()).willReturn("clientToken");
            given(jwtProvider.validateToken("clientToken")).willReturn(true);
            given(jwtProvider.getUserId("clientToken")).willReturn(1L);
            given(valueOperations.get("user:1:refresh_token")).willReturn("differentToken");

            assertThatThrownBy(() -> authService.refresh(req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> {
                        AuthException ae = (AuthException) e;
                        assertThat(ae.getErrorCode()).isEqualTo("REFRESH_TOKEN_MISMATCH");
                        assertThat(ae.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    });
        }
    }

    // ─────────────────────────────────────────────
    // 4. 로그아웃
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("로그아웃")
    class Logout {

        @Test
        @DisplayName("로그아웃 시 Redis의 Refresh Token 키가 삭제된다")
        void logout_deletesRefreshTokenFromRedis() {
            authService.logout(1L);

            verify(redisTemplate).delete("user:1:refresh_token");
        }
    }

    // ─────────────────────────────────────────────
    // 5. 아이디 찾기
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("아이디 찾기")
    class FindId {

        @Test
        @DisplayName("실명+이메일+인증번호 일치 시 마스킹된 아이디가 반환된다")
        void findId_success_returnsMaskedUsername() {
            // "testuser" → "test****"
            User user = buildUser();
            FindIdRequest req = mock(FindIdRequest.class);
            given(req.getName()).willReturn("홍길동");
            given(req.getEmail()).willReturn("test@example.com");
            given(req.getVerificationCode()).willReturn("123456");
            given(userRepository.findByRealNameAndEmail("홍길동", "test@example.com"))
                    .willReturn(Optional.of(user));

            FindIdResponse res = authService.findId(req);

            assertThat(res.getMaskedUsername()).isEqualTo("test****");
            verify(emailService).verifyCode("test@example.com", "find-id", "123456");
        }

        @Test
        @DisplayName("4자 이하 아이디는 마스킹 없이 그대로 반환된다")
        void findId_shortUsername_noMasking() {
            User shortUser = User.builder()
                    .userId(2L).username("kim").passwordHash("hash")
                    .realName("김철수").email("kim@example.com").nickname("김").preferredLanguage("ko")
                    .loginFailedCount(0).isActive(true).build();
            FindIdRequest req = mock(FindIdRequest.class);
            given(req.getName()).willReturn("김철수");
            given(req.getEmail()).willReturn("kim@example.com");
            given(req.getVerificationCode()).willReturn("111111");
            given(userRepository.findByRealNameAndEmail("김철수", "kim@example.com"))
                    .willReturn(Optional.of(shortUser));

            FindIdResponse res = authService.findId(req);

            assertThat(res.getMaskedUsername()).isEqualTo("kim");
        }

        @Test
        @DisplayName("일치하는 사용자가 없으면 USER_NOT_FOUND(404) 예외가 발생한다")
        void findId_notFound_throwsNotFound() {
            FindIdRequest req = mock(FindIdRequest.class);
            given(req.getName()).willReturn("없는사람");
            given(req.getEmail()).willReturn("none@example.com");
            given(req.getVerificationCode()).willReturn("000000");
            given(userRepository.findByRealNameAndEmail("없는사람", "none@example.com"))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.findId(req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> assertThat(((AuthException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    // ─────────────────────────────────────────────
    // 6. 비밀번호 변경 1단계 — 현재 비밀번호 검증
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("현재 비밀번호 검증 (비밀번호 변경 1단계)")
    class VerifyCurrentPassword {

        @Test
        @DisplayName("현재 비밀번호 일치 시 Redis에 5분 TTL 마커가 저장된다")
        void verifyCurrentPassword_success_savesRedisMarker() {
            User user = buildUser();
            VerifyCurrentPasswordRequest req = mock(VerifyCurrentPasswordRequest.class);
            given(req.getCurrentPassword()).willReturn("Test123!");
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(passwordEncoder.matches("Test123!", user.getPasswordHash())).willReturn(true);

            authService.verifyCurrentPassword(1L, req);

            verify(valueOperations).set(
                    eq("password:change:verified:1"),
                    eq("true"),
                    eq(5L),
                    eq(TimeUnit.MINUTES)
            );
        }

        @Test
        @DisplayName("현재 비밀번호 불일치 시 INVALID_PASSWORD(400) 예외가 발생한다")
        void verifyCurrentPassword_wrongPassword_throwsBadRequest() {
            User user = buildUser();
            VerifyCurrentPasswordRequest req = mock(VerifyCurrentPasswordRequest.class);
            given(req.getCurrentPassword()).willReturn("wrong!");
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(passwordEncoder.matches("wrong!", user.getPasswordHash())).willReturn(false);

            assertThatThrownBy(() -> authService.verifyCurrentPassword(1L, req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> {
                        AuthException ae = (AuthException) e;
                        assertThat(ae.getErrorCode()).isEqualTo("INVALID_PASSWORD");
                        assertThat(ae.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    });
        }

        @Test
        @DisplayName("비밀번호 불일치 시 Redis 마커가 저장되지 않는다")
        void verifyCurrentPassword_wrongPassword_doesNotSaveMarker() {
            User user = buildUser();
            VerifyCurrentPasswordRequest req = mock(VerifyCurrentPasswordRequest.class);
            given(req.getCurrentPassword()).willReturn("wrong!");
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(anyString(), anyString())).willReturn(false);

            try { authService.verifyCurrentPassword(1L, req); } catch (AuthException ignored) {}

            verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
        }
    }

    // ─────────────────────────────────────────────
    // 7. 비밀번호 변경 2단계
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("비밀번호 변경 (2단계)")
    class ChangePassword {

        @Test
        @DisplayName("Redis 마커 존재 + 비밀번호 일치 시 변경에 성공하고 Refresh Token이 삭제된다")
        void changePassword_success() {
            User user = buildUser();
            ChangePasswordRequest req = mock(ChangePasswordRequest.class);
            given(req.getNewPassword()).willReturn("NewPass123!");
            given(req.getConfirmNewPassword()).willReturn("NewPass123!");
            given(valueOperations.get("password:change:verified:1")).willReturn("true");
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(passwordEncoder.encode("NewPass123!")).willReturn("$2a$10$newHash");

            authService.changePassword(1L, req);

            verify(redisTemplate).delete("password:change:verified:1");
            verify(redisTemplate).delete("user:1:refresh_token");
        }

        @Test
        @DisplayName("1단계 검증 미완료(마커 없음) 시 PASSWORD_CHANGE_NOT_VERIFIED(403) 예외가 발생한다")
        void changePassword_noMarker_throwsForbidden() {
            ChangePasswordRequest req = mock(ChangePasswordRequest.class);
            given(valueOperations.get("password:change:verified:1")).willReturn(null);

            assertThatThrownBy(() -> authService.changePassword(1L, req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> {
                        AuthException ae = (AuthException) e;
                        assertThat(ae.getErrorCode()).isEqualTo("PASSWORD_CHANGE_NOT_VERIFIED");
                        assertThat(ae.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    });
        }

        @Test
        @DisplayName("새 비밀번호 불일치 시 PASSWORD_MISMATCH(400) 예외가 발생한다")
        void changePassword_passwordMismatch_throwsBadRequest() {
            ChangePasswordRequest req = mock(ChangePasswordRequest.class);
            given(req.getNewPassword()).willReturn("NewPass123!");
            given(req.getConfirmNewPassword()).willReturn("Different456!");
            given(valueOperations.get("password:change:verified:1")).willReturn("true");

            assertThatThrownBy(() -> authService.changePassword(1L, req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> {
                        AuthException ae = (AuthException) e;
                        assertThat(ae.getErrorCode()).isEqualTo("PASSWORD_MISMATCH");
                        assertThat(ae.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    });
        }

        @Test
        @DisplayName("예외 발생 시 Refresh Token이 삭제되지 않는다")
        void changePassword_onException_doesNotDeleteRefreshToken() {
            ChangePasswordRequest req = mock(ChangePasswordRequest.class);
            given(valueOperations.get("password:change:verified:1")).willReturn(null);

            try { authService.changePassword(1L, req); } catch (AuthException ignored) {}

            verify(redisTemplate, never()).delete("user:1:refresh_token");
        }
    }

    // ─────────────────────────────────────────────
    // 8. 비밀번호 재설정 1단계 — 이메일 인증
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("비밀번호 재설정 이메일 검증 (1단계)")
    class VerifyResetPassword {

        @Test
        @DisplayName("이메일 인증 + 계정 확인 성공 시 Redis에 5분 TTL 마커가 저장된다")
        void verifyResetPassword_success_savesRedisMarker() {
            User user = buildUser();
            VerifyResetPasswordRequest req = mock(VerifyResetPasswordRequest.class);
            given(req.getUsername()).willReturn("testuser");
            given(req.getEmail()).willReturn("test@example.com");
            given(req.getVerificationCode()).willReturn("654321");
            given(userRepository.findByUsernameAndEmail("testuser", "test@example.com"))
                    .willReturn(Optional.of(user));

            authService.verifyResetPassword(req);

            verify(emailService).verifyCode("test@example.com", "reset-pw", "654321");
            verify(valueOperations).set(
                    eq("password:reset:verified:testuser"),
                    eq("true"),
                    eq(5L),
                    eq(TimeUnit.MINUTES)
            );
        }

        @Test
        @DisplayName("아이디+이메일 불일치 시 USER_NOT_FOUND(404) 예외가 발생한다")
        void verifyResetPassword_userNotFound_throwsNotFound() {
            VerifyResetPasswordRequest req = mock(VerifyResetPasswordRequest.class);
            given(req.getUsername()).willReturn("unknown");
            given(req.getEmail()).willReturn("none@example.com");
            given(req.getVerificationCode()).willReturn("654321");
            given(userRepository.findByUsernameAndEmail("unknown", "none@example.com"))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.verifyResetPassword(req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> assertThat(((AuthException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }

        @Test
        @DisplayName("계정 미확인 시 Redis 마커가 저장되지 않는다")
        void verifyResetPassword_userNotFound_doesNotSaveMarker() {
            VerifyResetPasswordRequest req = mock(VerifyResetPasswordRequest.class);
            given(req.getUsername()).willReturn("unknown");
            given(req.getEmail()).willReturn("none@example.com");
            given(req.getVerificationCode()).willReturn("654321");
            given(userRepository.findByUsernameAndEmail(anyString(), anyString()))
                    .willReturn(Optional.empty());

            try { authService.verifyResetPassword(req); } catch (AuthException ignored) {}

            verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
        }
    }

    // ─────────────────────────────────────────────
    // 9. 비밀번호 재설정 2단계
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("비밀번호 재설정 (2단계)")
    class ResetPassword {

        @Test
        @DisplayName("Redis 마커 존재 + 비밀번호 일치 시 재설정에 성공하고 Refresh Token이 삭제된다")
        void resetPassword_success() {
            User user = buildUser();
            ResetPasswordRequest req = mock(ResetPasswordRequest.class);
            given(req.getUsername()).willReturn("testuser");
            given(req.getNewPassword()).willReturn("NewPass123!");
            given(req.getConfirmNewPassword()).willReturn("NewPass123!");
            given(valueOperations.get("password:reset:verified:testuser")).willReturn("true");
            given(userRepository.findByUsername("testuser")).willReturn(Optional.of(user));
            given(passwordEncoder.encode("NewPass123!")).willReturn("$2a$10$newHash");

            authService.resetPassword(req);

            verify(redisTemplate).delete("password:reset:verified:testuser");
            verify(redisTemplate).delete("user:1:refresh_token");
        }

        @Test
        @DisplayName("1단계 검증 미완료(마커 없음) 시 PASSWORD_RESET_NOT_VERIFIED(403) 예외가 발생한다")
        void resetPassword_noMarker_throwsForbidden() {
            ResetPasswordRequest req = mock(ResetPasswordRequest.class);
            given(req.getUsername()).willReturn("testuser");
            given(valueOperations.get("password:reset:verified:testuser")).willReturn(null);

            assertThatThrownBy(() -> authService.resetPassword(req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> {
                        AuthException ae = (AuthException) e;
                        assertThat(ae.getErrorCode()).isEqualTo("PASSWORD_RESET_NOT_VERIFIED");
                        assertThat(ae.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    });
        }

        @Test
        @DisplayName("새 비밀번호 불일치 시 PASSWORD_MISMATCH(400) 예외가 발생한다")
        void resetPassword_passwordMismatch_throwsBadRequest() {
            ResetPasswordRequest req = mock(ResetPasswordRequest.class);
            given(req.getUsername()).willReturn("testuser");
            given(req.getNewPassword()).willReturn("NewPass123!");
            given(req.getConfirmNewPassword()).willReturn("Different456!");
            given(valueOperations.get("password:reset:verified:testuser")).willReturn("true");

            assertThatThrownBy(() -> authService.resetPassword(req))
                    .isInstanceOf(AuthException.class)
                    .satisfies(e -> {
                        AuthException ae = (AuthException) e;
                        assertThat(ae.getErrorCode()).isEqualTo("PASSWORD_MISMATCH");
                        assertThat(ae.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    });
        }

        @Test
        @DisplayName("예외 발생 시 Refresh Token이 삭제되지 않는다")
        void resetPassword_onException_doesNotDeleteRefreshToken() {
            ResetPasswordRequest req = mock(ResetPasswordRequest.class);
            given(req.getUsername()).willReturn("testuser");
            given(valueOperations.get("password:reset:verified:testuser")).willReturn(null);

            try { authService.resetPassword(req); } catch (AuthException ignored) {}

            verify(redisTemplate, never()).delete("user:1:refresh_token");
        }
    }
}
