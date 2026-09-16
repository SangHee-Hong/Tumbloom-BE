package com.tumbloom.tumblerin.app.service;

import com.tumbloom.tumblerin.app.domain.RefreshToken;
import com.tumbloom.tumblerin.app.domain.RoleType;
import com.tumbloom.tumblerin.app.domain.User;
import com.tumbloom.tumblerin.app.dto.Authdto.LoginRequestDTO;
import com.tumbloom.tumblerin.app.dto.Authdto.SignupRequestDTO;
import com.tumbloom.tumblerin.app.dto.Authdto.TokenResponseDTO;
import com.tumbloom.tumblerin.app.repository.RefreshTokenRepository;
import com.tumbloom.tumblerin.app.repository.UserRepository;
import com.tumbloom.tumblerin.global.dto.ErrorCode;
import com.tumbloom.tumblerin.global.exception.BusinessException;
import com.tumbloom.tumblerin.global.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserService 리팩토링(인증/가입/토큰영속성/쿠키 처리 책임 분리, PasswordEncoder 인터페이스 의존 전환) 전
 * 현재 동작을 고정해두기 위한 회귀(regression) 테스트.
 *
 * DB, 실제 JWT 서명, Spring Security AuthenticationManager를 모두 mock 처리하는 순수 단위 테스트이며,
 * 리팩토링 전/후 이 테스트가 수정 없이 그대로 통과해야 "동작이 바뀌지 않았다"는 근거가 된다.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private UserService userService;

    @BeforeEach
    void setUp() {
        // BCryptPasswordEncoder는 순수 암호화 로직이라 mock 대신 실제 구현체를 사용 (인코딩 결과 검증을 위해 필요)
        userService = new UserService(userRepository, new BCryptPasswordEncoder(), authenticationManager, jwtTokenProvider, refreshTokenRepository);
    }

    private User user(Long id, String email) {
        return User.builder()
                .id(id)
                .nickname("tester")
                .email(email)
                .password("encoded-password")
                .roleType(RoleType.USER)
                .build();
    }

    // ===================== signup =====================

    @Test
    void signup_정상가입시_비밀번호가_인코딩되고_USER권한으로_저장된다() {
        SignupRequestDTO request = new SignupRequestDTO();
        request.setEmail("new@example.com");
        request.setPassword("plainPassword");
        request.setNickname("newbie");

        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());

        userService.signup(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getNickname()).isEqualTo("newbie");
        assertThat(saved.getEmail()).isEqualTo("new@example.com");
        assertThat(saved.getPassword()).isNotEqualTo("plainPassword"); // 평문 저장 금지
        assertThat(new BCryptPasswordEncoder().matches("plainPassword", saved.getPassword())).isTrue();
        assertThat(saved.getRoleType()).isEqualTo(RoleType.USER);
    }

    @Test
    void signup_이미_존재하는_이메일이면_409() {
        SignupRequestDTO request = new SignupRequestDTO();
        request.setEmail("dup@example.com");
        request.setPassword("pw");
        request.setNickname("dup");

        when(userRepository.findByEmail("dup@example.com")).thenReturn(Optional.of(user(1L, "dup@example.com")));

        assertThatThrownBy(() -> userService.signup(request))
                .isInstanceOf(BusinessException.class)
                // BusinessException.getMessage()는 detail이 아니라 ErrorCode 고유 메시지를 반환하는 현재 동작을 그대로 고정
                .hasMessage("이미 존재하는 리소스입니다.")
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.ALREADY_EXIST_SUBJECT_EXCEPTION);
                    assertThat(be.getDetail()).isEqualTo("이미 존재하는 이메일입니다.");
                });

        verify(userRepository, never()).save(any());
    }

    // ===================== login =====================

    @Test
    void login_정상로그인시_토큰을_발급하고_리프레시토큰을_저장하며_쿠키를_설정한다() {
        LoginRequestDTO req = new LoginRequestDTO("user@example.com", "pw");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user(1L, "user@example.com")));
        when(jwtTokenProvider.createAccessToken("user@example.com")).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken("user@example.com")).thenReturn("refresh-token");

        TokenResponseDTO result = userService.login(req, response);

        assertThat(result.getAccessToken()).isEqualTo("access-token");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("user@example.com");
        assertThat(captor.getValue().getRefreshToken()).isEqualTo("refresh-token");

        verify(jwtTokenProvider, times(1)).addRefreshTokenCookie(response, "refresh-token");
    }

    @Test
    void login_존재하지_않는_이메일이면_404이고_인증을_시도하지_않는다() {
        LoginRequestDTO req = new LoginRequestDTO("nouser@example.com", "pw");
        when(userRepository.findByEmail("nouser@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(req, response))
                .isInstanceOf(BusinessException.class)
                .hasMessage("해당 리소스를 찾지 못했습니다.")
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
                    assertThat(be.getDetail()).isEqualTo("해당 이메일은 존재하지 않습니다.");
                });

        verify(authenticationManager, never()).authenticate(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void login_비밀번호가_틀리면_401() {
        LoginRequestDTO req = new LoginRequestDTO("user@example.com", "wrong-pw");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user(1L, "user@example.com")));
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> userService.login(req, response))
                .isInstanceOf(BusinessException.class)
                .hasMessage("인증되지 않은 사용자입니다.")
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED_EXCEPTION);
                    assertThat(be.getDetail()).isEqualTo("해당 이메일 계정의 비밀번호가 올바르지 않습니다.");
                });

        verify(refreshTokenRepository, never()).save(any());
    }

    // ===================== logout =====================

    @Test
    void logout_정상로그아웃시_저장된_리프레시토큰을_삭제하고_쿠키를_제거한다() {
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("refreshToken", "refresh-token")});
        when(jwtTokenProvider.getUserEmailFromToken("refresh-token")).thenReturn("user@example.com");
        RefreshToken stored = RefreshToken.builder().email("user@example.com").refreshToken("refresh-token").build();
        when(refreshTokenRepository.findById("user@example.com")).thenReturn(Optional.of(stored));

        userService.logout(request, response);

        verify(jwtTokenProvider, times(1)).validateRefreshToken("refresh-token");
        verify(refreshTokenRepository, times(1)).delete(stored);
        verify(jwtTokenProvider, times(1)).removeRefreshTokenCookie(response);
    }

    @Test
    void logout_쿠키가_아예_없으면_404() {
        when(request.getCookies()).thenReturn(null);

        assertThatThrownBy(() -> userService.logout(request, response))
                .isInstanceOf(BusinessException.class)
                .hasMessage("저장된 Refresh Token이 존재하지 않습니다.")
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
                    assertThat(be.getDetail()).isEqualTo("쿠키가 없습니다.");
                });
    }

    @Test
    void logout_쿠키에_refreshToken이_없으면_404() {
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("other", "v")});

        assertThatThrownBy(() -> userService.logout(request, response))
                .isInstanceOf(BusinessException.class)
                .hasMessage("저장된 Refresh Token이 존재하지 않습니다.")
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
                    assertThat(be.getDetail()).isEqualTo("쿠키에 Refresh Token이 없음");
                });
    }

    @Test
    void logout_저장된_리프레시토큰이_없으면_404() {
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("refreshToken", "refresh-token")});
        when(jwtTokenProvider.getUserEmailFromToken("refresh-token")).thenReturn("user@example.com");
        when(refreshTokenRepository.findById("user@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.logout(request, response))
                .isInstanceOf(BusinessException.class)
                .hasMessage("저장된 Refresh Token이 존재하지 않습니다.")
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
                    assertThat(be.getDetail()).isEqualTo("로그아웃 대상 Refresh Token이 존재하지 않습니다.");
                });
    }

    @Test
    void logout_쿠키값과_저장된_토큰이_다르면_401_불일치() {
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("refreshToken", "cookie-token")});
        when(jwtTokenProvider.getUserEmailFromToken("cookie-token")).thenReturn("user@example.com");
        RefreshToken stored = RefreshToken.builder().email("user@example.com").refreshToken("different-token").build();
        when(refreshTokenRepository.findById("user@example.com")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> userService.logout(request, response))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Refresh Token이 일치하지 않습니다.")
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_MISMATCH);
                    assertThat(be.getDetail()).isEqualTo("Refresh Token이 일치하지 않아 로그아웃할 수 없습니다.");
                });

        verify(refreshTokenRepository, never()).delete(any());
        verify(jwtTokenProvider, never()).removeRefreshTokenCookie(any());
    }

    // ===================== refresh =====================

    @Test
    void refresh_정상재발급시_새로운_액세스토큰을_반환한다() {
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("refreshToken", "refresh-token")});
        when(jwtTokenProvider.getUserEmailFromToken("refresh-token")).thenReturn("user@example.com");
        RefreshToken stored = RefreshToken.builder().email("user@example.com").refreshToken("refresh-token").build();
        when(refreshTokenRepository.findById("user@example.com")).thenReturn(Optional.of(stored));
        when(jwtTokenProvider.createAccessToken("user@example.com")).thenReturn("new-access-token");

        TokenResponseDTO result = userService.refresh(request);

        assertThat(result.getAccessToken()).isEqualTo("new-access-token");
        verify(jwtTokenProvider, times(1)).validateRefreshToken("refresh-token");
    }

    @Test
    void refresh_쿠키가_아예_없으면_404() {
        when(request.getCookies()).thenReturn(null);

        assertThatThrownBy(() -> userService.refresh(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("저장된 Refresh Token이 존재하지 않습니다.")
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
                    assertThat(be.getDetail()).isEqualTo("쿠키가 없습니다.");
                });
    }

    @Test
    void refresh_쿠키에_refreshToken이_없으면_404() {
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("other", "v")});

        assertThatThrownBy(() -> userService.refresh(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("저장된 Refresh Token이 존재하지 않습니다.")
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
                    assertThat(be.getDetail()).isEqualTo("Refresh Token이 쿠키에 없음");
                });
    }

    @Test
    void refresh_저장된_리프레시토큰이_없으면_404() {
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("refreshToken", "refresh-token")});
        when(jwtTokenProvider.getUserEmailFromToken("refresh-token")).thenReturn("user@example.com");
        when(refreshTokenRepository.findById("user@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.refresh(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("저장된 Refresh Token이 존재하지 않습니다.")
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
                    assertThat(be.getDetail()).isEqualTo("Refresh Token이 존재하지 않습니다.");
                });
    }

    @Test
    void refresh_쿠키값과_저장된_토큰이_다르면_401_불일치() {
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("refreshToken", "cookie-token")});
        when(jwtTokenProvider.getUserEmailFromToken("cookie-token")).thenReturn("user@example.com");
        RefreshToken stored = RefreshToken.builder().email("user@example.com").refreshToken("different-token").build();
        when(refreshTokenRepository.findById("user@example.com")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> userService.refresh(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Refresh Token이 일치하지 않습니다.")
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_MISMATCH);
                    assertThat(be.getDetail()).isEqualTo("Refresh Token이 일치하지 않습니다.");
                });
    }
}