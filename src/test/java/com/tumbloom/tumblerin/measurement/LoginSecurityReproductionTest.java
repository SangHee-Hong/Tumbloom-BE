package com.tumbloom.tumblerin.measurement;

import com.tumbloom.tumblerin.app.domain.RefreshToken;
import com.tumbloom.tumblerin.app.dto.Authdto.LoginRequestDTO;
import com.tumbloom.tumblerin.app.dto.Authdto.TokenResponseDTO;
import com.tumbloom.tumblerin.global.dto.ErrorCode;
import com.tumbloom.tumblerin.global.exception.BusinessException;
import com.tumbloom.tumblerin.global.security.JwtAuthenticationFilter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static com.tumbloom.tumblerin.measurement.LoginFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [측정 계획 2] 로그인 보안 문제 재현 테스트.
 *
 * 각 테스트는 "현재 이렇게 동작한다"를 고정한 기록이다. 통과 = 문제가 지금도 재현된다는 뜻.
 * 해당 문제를 고치면 이 테스트는 의도적으로 실패하므로, 그때 기대값을 고친 동작으로 바꾼다.
 */
@Tag("measurement")
class LoginSecurityReproductionTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ===================== 보안 1. 응답 코드로 계정 존재 여부 노출 =====================

    @Test
    void 보안1_없는_이메일은_404_틀린_비밀번호는_401로_응답이_달라_계정_존재여부가_드러난다() {
        LoginFixture f = new LoginFixture();

        BusinessException unknownEmail = catchThrowableOfType(
                () -> f.userService.login(new LoginRequestDTO(UNKNOWN_EMAIL, WRONG_PASSWORD), new MockHttpServletResponse()),
                BusinessException.class);
        BusinessException wrongPassword = catchThrowableOfType(
                () -> f.userService.login(new LoginRequestDTO(EMAIL, WRONG_PASSWORD), new MockHttpServletResponse()),
                BusinessException.class);

        report("보안1 없는 이메일   ", unknownEmail.getErrorCode().getHttpStatus() + " / " + unknownEmail.getDetail());
        report("보안1 틀린 비밀번호 ", wrongPassword.getErrorCode().getHttpStatus() + " / " + wrongPassword.getDetail());

        assertThat(unknownEmail.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(unknownEmail.getErrorCode().getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(wrongPassword.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED_EXCEPTION);
        assertThat(wrongPassword.getErrorCode().getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ===================== 보안 2. 응답 시간으로 계정 존재 여부 노출 =====================

    @Test
    void 보안2_없는_이메일은_BCrypt를_건너뛰어_응답시간_차이로_계정_존재여부가_드러난다() {
        LoginFixture f = new LoginFixture();
        LoginRequestDTO unknownEmail = new LoginRequestDTO(UNKNOWN_EMAIL, WRONG_PASSWORD);
        LoginRequestDTO wrongPassword = new LoginRequestDTO(EMAIL, WRONG_PASSWORD);

        Stats unknownStats = measure(5, 30, () -> f.userService.login(unknownEmail, new MockHttpServletResponse()));
        Stats wrongStats = measure(5, 30, () -> f.userService.login(wrongPassword, new MockHttpServletResponse()));

        report("보안2 UserService.login 없는 이메일  ", unknownStats);
        report("보안2 UserService.login 틀린 비밀번호", wrongStats);
        report("보안2 p50 배율(틀린 비밀번호 / 없는 이메일)", String.format("%.0fx", wrongStats.p50() / unknownStats.p50()));

        // 비교군: UserService의 사전 조회 없이 Spring AuthenticationManager만 쓰면 어떻게 되는가
        Stats amUnknown = measure(5, 30, () -> f.authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(UNKNOWN_EMAIL, WRONG_PASSWORD)));
        Stats amWrong = measure(5, 30, () -> f.authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(EMAIL, WRONG_PASSWORD)));

        report("보안2 [비교] AuthenticationManager 없는 이메일  ", amUnknown);
        report("보안2 [비교] AuthenticationManager 틀린 비밀번호", amWrong);
        report("보안2 [비교] p50 배율", String.format("%.1fx", amWrong.p50() / amUnknown.p50()));

        // 현재: 없는 이메일은 BCrypt를 안 거쳐서 훨씬 빠르다
        assertThat(wrongStats.p50()).isGreaterThan(unknownStats.p50() * 5);
        // 비교군: Spring은 없는 사용자에도 더미 해시로 BCrypt를 돌려(타이밍 공격 완화) 둘 다 BCrypt 시간이 걸린다
        assertThat(amUnknown.p50()).isGreaterThan(unknownStats.p50() * 5);
    }

    // ===================== 보안 3. 무차별 대입 제한 없음 =====================

    @Test
    void 보안3_비밀번호를_20번_연속_틀려도_막히지_않고_이후_정상_로그인된다() {
        LoginFixture f = new LoginFixture();
        LoginRequestDTO wrong = new LoginRequestDTO(EMAIL, WRONG_PASSWORD);

        int unauthorizedCount = 0;
        for (int i = 0; i < 20; i++) {
            BusinessException e = catchThrowableOfType(
                    () -> f.userService.login(wrong, new MockHttpServletResponse()), BusinessException.class);
            if (e.getErrorCode() == ErrorCode.UNAUTHORIZED_EXCEPTION) {
                unauthorizedCount++;
            }
        }

        TokenResponseDTO afterAttack = f.userService.login(new LoginRequestDTO(EMAIL, CORRECT_PASSWORD), new MockHttpServletResponse());

        report("보안3 연속 실패 20회 중 401 응답", unauthorizedCount + "회 (잠금/지연/429 없음)");
        report("보안3 실패 20회 후 정상 비밀번호 로그인", afterAttack.getAccessToken() != null ? "성공" : "실패");

        assertThat(unauthorizedCount).isEqualTo(20);
        assertThat(afterAttack.getAccessToken()).isNotBlank();
    }

    // ===================== 보안 5. 로그아웃 후에도 Access Token 유효 =====================

    @Test
    void 보안5_로그아웃해도_이미_발급된_AccessToken으로_인증이_통과된다() throws Exception {
        LoginFixture f = new LoginFixture();

        // 로그인
        MockHttpServletResponse loginResponse = new MockHttpServletResponse();
        String accessToken = f.userService.login(new LoginRequestDTO(EMAIL, CORRECT_PASSWORD), loginResponse).getAccessToken();
        String refreshToken = extractRefreshToken(loginResponse);

        // 로그아웃 (DB에 RT가 저장되어 있는 상태를 가정)
        RefreshToken stored = RefreshToken.builder().email(EMAIL).refreshToken(refreshToken).build();
        when(f.refreshTokenRepository.findById(EMAIL)).thenReturn(Optional.of(stored));

        MockHttpServletRequest logoutRequest = new MockHttpServletRequest();
        logoutRequest.setCookies(new Cookie("refreshToken", refreshToken));
        f.userService.logout(logoutRequest, new MockHttpServletResponse());
        verify(f.refreshTokenRepository).delete(stored);

        // 로그아웃 후, 같은 Access Token으로 인증이 필요한 API 요청
        MockHttpServletRequest apiRequest = new MockHttpServletRequest("GET", "/api/mypage");
        apiRequest.setRequestURI("/api/mypage");
        apiRequest.addHeader("Authorization", "Bearer " + accessToken);
        MockHttpServletResponse apiResponse = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new JwtAuthenticationFilter(f.jwtTokenProvider).doFilter(apiRequest, apiResponse, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        report("보안5 로그아웃 후 AT로 요청", "status=" + apiResponse.getStatus()
                + ", 다음 필터 진행=" + (chain.getRequest() != null)
                + ", 인증 사용자=" + (auth == null ? null : auth.getName()));

        assertThat(apiResponse.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo(EMAIL);

        // 반면 RT는 무효화되어 재발급은 막힌다
        when(f.refreshTokenRepository.findById(EMAIL)).thenReturn(Optional.empty());
        MockHttpServletRequest refreshRequest = new MockHttpServletRequest();
        refreshRequest.setCookies(new Cookie("refreshToken", refreshToken));
        assertThatThrownBy(() -> f.userService.refresh(refreshRequest))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        report("보안5 로그아웃 후 RT로 재발급", "거부됨 (REFRESH_TOKEN_NOT_FOUND)");
    }

    // ===================== 참고: AuthenticationManager의 비밀번호 오류 예외 =====================

    @Test
    void 참고_AuthenticationManager는_없는_이메일도_BadCredentialsException으로_감춘다() {
        LoginFixture f = new LoginFixture();

        assertThatThrownBy(() -> f.authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(UNKNOWN_EMAIL, WRONG_PASSWORD)))
                .isInstanceOf(BadCredentialsException.class);
        report("참고 AuthenticationManager 없는 이메일 예외", "BadCredentialsException (UsernameNotFound를 감춤)");
    }

    private String extractRefreshToken(MockHttpServletResponse response) {
        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie).startsWith("refreshToken=");
        return setCookie.substring("refreshToken=".length(), setCookie.indexOf(';'));
    }
}
