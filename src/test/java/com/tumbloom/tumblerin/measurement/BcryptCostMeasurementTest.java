package com.tumbloom.tumblerin.measurement;

import com.tumbloom.tumblerin.app.dto.Authdto.LoginRequestDTO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static com.tumbloom.tumblerin.measurement.LoginFixture.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * [측정 계획 1] BCrypt 비용과 로그인 시간 중 BCrypt 비중 측정.
 *
 * DB는 mock이므로 여기서 재는 "로그인 시간"은 DB 왕복을 뺀 순수 애플리케이션 처리 시간이다.
 * 결과는 표준출력의 [MEASURE] 줄로 남기며, 단정(assert)은 측정이 정상 수행됐는지만 확인한다.
 */
@Tag("measurement")
class BcryptCostMeasurementTest {

    private static final int WARMUP = 5;

    @Test
    void BCrypt_강도별_encode_matches_시간() {
        for (int strength : new int[]{10, 12}) {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(strength);
            String hash = encoder.encode(CORRECT_PASSWORD);
            int n = strength == 10 ? 50 : 15;

            Stats encode = measure(WARMUP, n, () -> encoder.encode(CORRECT_PASSWORD));
            Stats matches = measure(WARMUP, n, () -> encoder.matches(CORRECT_PASSWORD, hash));

            report("BCrypt(strength=" + strength + ") encode ", encode);
            report("BCrypt(strength=" + strength + ") matches", matches);

            assertThat(encoder.matches(CORRECT_PASSWORD, hash)).isTrue();
        }
    }

    @Test
    void 로그인_성공_전체시간_중_BCrypt_비중() {
        LoginFixture f = new LoginFixture();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode(CORRECT_PASSWORD);
        LoginRequestDTO request = new LoginRequestDTO(EMAIL, CORRECT_PASSWORD);

        // 순서대로 재면 JIT/GC 상태 차이가 한쪽에만 실려서, 한 번씩 번갈아 측정한다
        int n = 50;
        double[] loginSamples = new double[n];
        double[] bcryptSamples = new double[n];
        for (int i = 0; i < WARMUP + n; i++) {
            long t0 = System.nanoTime();
            f.userService.login(request, new MockHttpServletResponse());
            long t1 = System.nanoTime();
            encoder.matches(CORRECT_PASSWORD, hash);
            long t2 = System.nanoTime();
            if (i >= WARMUP) {
                loginSamples[i - WARMUP] = (t1 - t0) / 1_000_000.0;
                bcryptSamples[i - WARMUP] = (t2 - t1) / 1_000_000.0;
            }
        }
        Stats login = Stats.of(loginSamples);
        Stats bcrypt = Stats.of(bcryptSamples);

        report("UserService.login 성공(DB mock)", login);
        report("BCrypt matches(strength=10)     ", bcrypt);
        report("로그인 시간 중 BCrypt 비중(p50)  ", String.format("%.1f%%", bcrypt.p50() / login.p50() * 100));

        assertThat(login.p50()).isGreaterThan(0);
    }
}
