package com.tumbloom.tumblerin.measurement;

import com.tumbloom.tumblerin.app.domain.RoleType;
import com.tumbloom.tumblerin.app.domain.User;
import com.tumbloom.tumblerin.app.repository.RefreshTokenRepository;
import com.tumbloom.tumblerin.app.repository.UserRepository;
import com.tumbloom.tumblerin.app.security.CustomUserDetailsService;
import com.tumbloom.tumblerin.app.service.RefreshTokenService;
import com.tumbloom.tumblerin.app.service.UserService;
import com.tumbloom.tumblerin.global.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 로그인 측정/재현 테스트용 조립 객체.
 *
 * DB(Repository)만 mock이고, 나머지는 운영과 같은 실제 구현체로 조립한다.
 * - 실제 BCryptPasswordEncoder (기본 강도 10)
 * - 실제 ProviderManager + DaoAuthenticationProvider + CustomUserDetailsService (Spring Boot 자동 구성과 동일한 조합)
 * - 실제 JwtTokenProvider (테스트용 비밀키로 서명)
 * - 실제 RefreshTokenService
 */
class LoginFixture {

    static final String EMAIL = "user@example.com";
    static final String CORRECT_PASSWORD = "correct-password";
    static final String WRONG_PASSWORD = "wrong-password";
    static final String UNKNOWN_EMAIL = "nouser@example.com";

    private static final String TEST_JWT_SECRET = "measurement-test-secret-key-0123456789-abcdefghij";

    final UserRepository userRepository = mock(UserRepository.class);
    final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    final CustomUserDetailsService userDetailsService = new CustomUserDetailsService(userRepository);
    final AuthenticationManager authenticationManager;
    final JwtTokenProvider jwtTokenProvider;
    final RefreshTokenService refreshTokenService = new RefreshTokenService(refreshTokenRepository);
    final UserService userService;

    LoginFixture() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        authenticationManager = new ProviderManager(provider);

        jwtTokenProvider = new JwtTokenProvider(userDetailsService);
        ReflectionTestUtils.setField(jwtTokenProvider, "secretKey", TEST_JWT_SECRET);
        ReflectionTestUtils.invokeMethod(jwtTokenProvider, "init");

        userService = new UserService(userRepository, passwordEncoder, authenticationManager, jwtTokenProvider, refreshTokenService);

        User user = User.builder()
                .id(1L)
                .nickname("tester")
                .email(EMAIL)
                .password(passwordEncoder.encode(CORRECT_PASSWORD))
                .roleType(RoleType.USER)
                .build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.findByEmail(UNKNOWN_EMAIL)).thenReturn(Optional.empty());
    }

    // ===================== 시간 측정 유틸 =====================

    /** 실행 시간(ms) 표본의 요약 통계 */
    record Stats(int n, double avg, double p50, double p95, double max) {
        static Stats of(double[] samplesMs) {
            double[] s = samplesMs.clone();
            Arrays.sort(s);
            return new Stats(s.length, Arrays.stream(s).average().orElse(0), percentile(s, 50), percentile(s, 95), s[s.length - 1]);
        }

        private static double percentile(double[] sorted, int p) {
            int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
            return sorted[Math.max(0, Math.min(idx, sorted.length - 1))];
        }

        @Override
        public String toString() {
            return String.format("n=%d, avg=%.3fms, p50=%.3fms, p95=%.3fms, max=%.3fms", n, avg, p50, p95, max);
        }
    }

    /** 예외가 나도 시간만 재고 넘어간다 (실패 경로 측정용) */
    static Stats measure(int warmup, int n, Runnable action) {
        for (int i = 0; i < warmup; i++) {
            runIgnoringException(action);
        }
        double[] samples = new double[n];
        for (int i = 0; i < n; i++) {
            long start = System.nanoTime();
            runIgnoringException(action);
            samples[i] = (System.nanoTime() - start) / 1_000_000.0;
        }
        return Stats.of(samples);
    }

    private static void runIgnoringException(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ignored) {
        }
    }

    static void report(String label, Object value) {
        System.out.println("[MEASURE] " + label + " : " + value);
    }
}
