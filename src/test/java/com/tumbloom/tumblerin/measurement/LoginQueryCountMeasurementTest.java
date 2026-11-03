package com.tumbloom.tumblerin.measurement;

import com.tumbloom.tumblerin.app.dto.Authdto.LoginRequestDTO;
import com.tumbloom.tumblerin.app.dto.Authdto.SignupRequestDTO;
import com.tumbloom.tumblerin.app.repository.RefreshTokenRepository;
import com.tumbloom.tumblerin.app.repository.UserRepository;
import com.tumbloom.tumblerin.app.service.RefreshTokenService;
import com.tumbloom.tumblerin.app.service.UserService;
import com.tumbloom.tumblerin.global.security.JwtTokenProvider;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static com.tumbloom.tumblerin.measurement.LoginFixture.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * [측정 계획 3] 실제 PostgreSQL(PostGIS)에서 로그인/로그아웃/재발급/인증 요청 1회당 실행되는 SQL 수 측정.
 *
 * - Hibernate Statistics는 이 테스트에서만 켠다 (운영 설정 변경 없음).
 * - 개발 DB 오염을 막기 위해 DB_JDBC_URL이 측정 전용 DB(tumblerin_test)를 가리킬 때만 실행된다.
 * - @Transactional을 붙이지 않는다. 롤백되면 INSERT/UPDATE가 flush되지 않아 SQL 수가 실제보다 적게 잡히기 때문.
 *   대신 테스트 계정 데이터는 @AfterAll에서 직접 지운다.
 */
@Tag("measurement")
@EnabledIfEnvironmentVariable(named = "DB_JDBC_URL", matches = ".*/tumblerin_test.*")
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.SQL=DEBUG",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LoginQueryCountMeasurementTest {

    private static final String TEST_EMAIL = "measure-login@example.com";

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private RefreshTokenService refreshTokenService;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics stats;

    @BeforeAll
    void setUp() {
        stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        cleanUp();

        SignupRequestDTO signup = new SignupRequestDTO();
        signup.setEmail(TEST_EMAIL);
        signup.setPassword(CORRECT_PASSWORD);
        signup.setNickname("measure");
        userService.signup(signup);
    }

    @AfterAll
    void cleanUp() {
        refreshTokenRepository.findById(TEST_EMAIL).ifPresent(refreshTokenRepository::delete);
        userRepository.findByEmail(TEST_EMAIL).ifPresent(userRepository::delete);
    }

    /** 측정 대상 실행 전후의 Hibernate 통계 차이 */
    private record Counts(long statements, long queries, long entityLoads, long inserts, long updates, long deletes, long transactions) {
        @Override
        public String toString() {
            return String.format("SQL=%d (JPQL 쿼리=%d, 엔티티 로드=%d, INSERT=%d, UPDATE=%d, DELETE=%d), 트랜잭션=%d",
                    statements, queries, entityLoads, inserts, updates, deletes, transactions);
        }
    }

    private Counts count(Runnable action) {
        stats.clear();
        try {
            action.run();
        } catch (RuntimeException ignored) {
            // 실패 경로도 그때까지 실행된 SQL을 센다
        }
        return new Counts(
                stats.getPrepareStatementCount(),
                stats.getQueryExecutionCount(),
                stats.getEntityLoadCount(),
                stats.getEntityInsertCount(),
                stats.getEntityUpdateCount(),
                stats.getEntityDeleteCount(),
                stats.getTransactionCount());
    }

    private MockHttpServletResponse login() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        userService.login(new LoginRequestDTO(TEST_EMAIL, CORRECT_PASSWORD), response);
        return response;
    }

    private MockHttpServletRequest requestWithRefreshCookie(MockHttpServletResponse loginResponse) {
        String setCookie = loginResponse.getHeader("Set-Cookie");
        String refreshToken = setCookie.substring("refreshToken=".length(), setCookie.indexOf(';'));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refreshToken", refreshToken));
        return request;
    }

    // ===================== 로그인 =====================

    @Test
    @Order(1)
    void 로그인_성공_첫_로그인_RT_없음() {
        refreshTokenRepository.findById(TEST_EMAIL).ifPresent(refreshTokenRepository::delete);

        Counts c = count(this::login);
        report("SQL 로그인 성공(첫 로그인, RT 행 없음)", c);

        assertThat(c.statements()).isGreaterThan(0);
        assertThat(c.inserts()).isEqualTo(1);
    }

    @Test
    @Order(2)
    void 로그인_성공_재로그인_RT_있음() {
        login();

        Counts c = count(this::login);
        report("SQL 로그인 성공(재로그인, RT 행 있음)", c);

        assertThat(c.updates()).isEqualTo(1);
    }

    @Test
    @Order(3)
    void 로그인_실패_없는_이메일() {
        Counts c = count(() -> userService.login(new LoginRequestDTO(UNKNOWN_EMAIL, WRONG_PASSWORD), new MockHttpServletResponse()));
        report("SQL 로그인 실패(없는 이메일)", c);
    }

    @Test
    @Order(4)
    void 로그인_실패_틀린_비밀번호() {
        Counts c = count(() -> userService.login(new LoginRequestDTO(TEST_EMAIL, WRONG_PASSWORD), new MockHttpServletResponse()));
        report("SQL 로그인 실패(틀린 비밀번호)", c);
    }

    // ===================== 인증된 요청 / 재발급 / 로그아웃 =====================

    @Test
    @Order(5)
    void 인증된_API_요청_1회_필터의_사용자_조회() {
        String accessToken = jwtTokenProvider.createAccessToken(TEST_EMAIL);

        Counts c = count(() -> jwtTokenProvider.getAuthentication(accessToken));
        report("SQL 인증된 API 요청 1회(JwtAuthenticationFilter)", c);
    }

    @Test
    @Order(6)
    void 토큰_재발급() {
        MockHttpServletRequest request = requestWithRefreshCookie(login());

        Counts c = count(() -> userService.refresh(request));
        report("SQL 토큰 재발급(/refresh)", c);
    }

    @Test
    @Order(7)
    void 로그아웃() {
        MockHttpServletRequest request = requestWithRefreshCookie(login());

        Counts c = count(() -> userService.logout(request, new MockHttpServletResponse()));
        report("SQL 로그아웃", c);

        assertThat(c.deletes()).isEqualTo(1);
    }

    // ===================== DB 포함 로그인 시간 =====================

    @Test
    @Order(8)
    void 로그인_성공_시간_DB_포함_단계별() {
        // SQL 로그 출력 시간이 측정값에 섞이지 않도록 시간 측정 중에는 끈다
        LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());
        loggingSystem.setLogLevel("org.hibernate.SQL", LogLevel.INFO);
        try {
            Stats login = measure(5, 30, this::login);
            Stats findUser = measure(5, 30, () -> userRepository.findByEmail(TEST_EMAIL));
            Stats findRefreshToken = measure(5, 30, () -> refreshTokenRepository.findById(TEST_EMAIL));
            Stats issueRefreshToken = measure(5, 30, () -> refreshTokenService.issue(TEST_EMAIL, jwtTokenProvider.createRefreshToken(TEST_EMAIL)));

            report("시간 로그인 성공(실제 DB 포함)              ", login);
            report("시간 └ 사용자 조회 findByEmail (users+user_preference)", findUser);
            report("시간 └ RT 조회 findById                          ", findRefreshToken);
            report("시간 └ RT 저장 issue (SELECT+UPDATE, 트랜잭션 커밋)  ", issueRefreshToken);

            assertThat(login.p50()).isGreaterThan(0);
        } finally {
            loggingSystem.setLogLevel("org.hibernate.SQL", LogLevel.DEBUG);
        }
    }
}
