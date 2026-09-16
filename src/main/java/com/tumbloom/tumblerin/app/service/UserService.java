package com.tumbloom.tumblerin.app.service;

import com.tumbloom.tumblerin.app.domain.RefreshToken;
import com.tumbloom.tumblerin.app.repository.UserRepository;
import com.tumbloom.tumblerin.app.domain.RoleType;
import com.tumbloom.tumblerin.app.domain.User;
import com.tumbloom.tumblerin.app.dto.Authdto.LoginRequestDTO;
import com.tumbloom.tumblerin.app.dto.Authdto.TokenResponseDTO;
import com.tumbloom.tumblerin.app.dto.Authdto.SignupRequestDTO;
import com.tumbloom.tumblerin.global.dto.ErrorCode;
import com.tumbloom.tumblerin.global.exception.BusinessException;
import com.tumbloom.tumblerin.global.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    public void signup(SignupRequestDTO request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BusinessException(ErrorCode.ALREADY_EXIST_SUBJECT_EXCEPTION, "이미 존재하는 이메일입니다.");
        }

        User user = User.builder()
                .nickname(request.getNickname())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .roleType(RoleType.USER)
                .build();

        userRepository.save(user);
    }

    public TokenResponseDTO login(LoginRequestDTO request, HttpServletResponse response) {

        userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"해당 이메일은 존재하지 않습니다."));

        // 비밀번호 검증
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (BadCredentialsException ex) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_EXCEPTION, "해당 이메일 계정의 비밀번호가 올바르지 않습니다."); // 401
        }


        String email = request.getEmail();
        String accessToken = jwtTokenProvider.createAccessToken(email);
        String refreshToken = jwtTokenProvider.createRefreshToken(email);

        refreshTokenService.issue(email, refreshToken);

        // 쿠키에 refreshtoken 저장
        jwtTokenProvider.addRefreshTokenCookie(response, refreshToken);

        return new TokenResponseDTO(accessToken);
    }

    // 로그아웃
    public void logout(HttpServletRequest request, HttpServletResponse response) {

        String refreshToken = extractRefreshTokenCookie(request, "쿠키가 없습니다.", "쿠키에 Refresh Token이 없음");

        jwtTokenProvider.validateRefreshToken(refreshToken);

        String email = jwtTokenProvider.getUserEmailFromToken(refreshToken);
        RefreshToken storedToken = refreshTokenService.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND, "로그아웃 대상 Refresh Token이 존재하지 않습니다."));
        if (!refreshTokenService.matches(storedToken, refreshToken)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_MISMATCH, "Refresh Token이 일치하지 않아 로그아웃할 수 없습니다.");
        }
        refreshTokenService.delete(storedToken);

        jwtTokenProvider.removeRefreshTokenCookie(response);
    }

    // 토큰 재발급
    public TokenResponseDTO refresh(HttpServletRequest request) {

        String refreshToken = extractRefreshTokenCookie(request, "쿠키가 없습니다.", "Refresh Token이 쿠키에 없음");

        jwtTokenProvider.validateRefreshToken(refreshToken);
        String email = jwtTokenProvider.getUserEmailFromToken(refreshToken);

        RefreshToken storedToken = refreshTokenService.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND, "Refresh Token이 존재하지 않습니다."));

        if (!refreshTokenService.matches(storedToken, refreshToken)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_MISMATCH, "Refresh Token이 일치하지 않습니다.");
        }

        String newAccessToken = jwtTokenProvider.createAccessToken(email);

        return new TokenResponseDTO(newAccessToken);
    }

    // 쿠키에서 refreshToken 값 추출 (logout/refresh에 중복되어 있던 로직을 통합, 호출부별 에러 메시지는 그대로 유지)
    private String extractRefreshTokenCookie(HttpServletRequest request, String noCookiesDetail, String noRefreshTokenDetail) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND, noCookiesDetail);
        }

        return Arrays.stream(cookies)
                .filter(c -> c.getName().equals("refreshToken"))
                .findFirst()
                .map(Cookie::getValue)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND, noRefreshTokenDetail));
    }
}
