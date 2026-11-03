// 로그인 API 부하 테스트 (측정 계획 4번)
//
// 실행 예 (Docker, Windows/Mac):
//   docker run --rm -i -e BASE_URL=http://host.docker.internal:18080 -e RATE=50 \
//     grafana/k6:0.54.0 run - < perf/k6/login.js
//
// 환경변수
//   BASE_URL    대상 서버 (기본 http://localhost:18080)
//   RATE        초당 로그인 요청 수 (기본 20)
//   DURATION    측정 시간 (기본 30s)
//   USER_COUNT  테스트 계정 수 (기본 200)
//   SCENARIO    success(정상 로그인, 기본) | unknown(없는 이메일)
//
// - 테스트 계정은 setup()에서 /api/auth/signup으로 만든다. 이미 있으면 409라 무시한다.
// - 모든 요청이 같은 계정이면 refresh_token의 같은 행을 동시에 UPDATE하면서 락 대기가 생겨
//   실제와 다른 결과가 나오므로, 매 요청마다 USER_COUNT개 계정 중 하나를 무작위로 고른다.
// - 측정이 끝나면 테스트 계정(k6-user-*)과 refresh_token 행은 DB에서 직접 지운다.

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// 응답 결과별 건수 (실패가 서버 에러인지, 연결 실패/타임아웃인지 구분하기 위함)
const loginResult = new Counter('login_result');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:18080';
const RATE = Number(__ENV.RATE || 20);
const DURATION = __ENV.DURATION || '30s';
const USER_COUNT = Number(__ENV.USER_COUNT || 200);
const SCENARIO = __ENV.SCENARIO || 'success';
const PASSWORD = 'k6-password-1234';
const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

export const options = {
    setupTimeout: '180s',
    scenarios: {
        login: {
            // 응답이 느려져도 요청 속도를 유지하는 방식 (실제 사용자 유입처럼 초당 RATE건을 계속 보냄)
            executor: 'constant-arrival-rate',
            rate: RATE,
            timeUnit: '1s',
            duration: DURATION,
            preAllocatedVUs: Math.max(50, RATE * 2),
            maxVUs: Math.max(300, RATE * 6),
        },
    },
    summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    // setup()의 회원가입 요청을 빼고 로그인 요청만 따로 집계하기 위한 하위 지표.
    // p95 < 500ms를 합격 기준으로 둔다 (넘으면 k6가 실패 표시, 측정 자체는 계속 진행)
    thresholds: {
        'http_req_duration{name:login}': ['p(95)<500'],
        'http_req_failed{name:login}': SCENARIO === 'unknown' ? [] : ['rate<0.01'],
        'http_reqs{name:login}': [],
        'login_result{result:ok}': [],
        'login_result{result:http_error}': [],
        'login_result{result:network_error}': [],
    },
};

const email = (i) => `k6-user-${i}@example.com`;

export function setup() {
    const BATCH = 20;
    for (let start = 0; start < USER_COUNT; start += BATCH) {
        const requests = [];
        for (let i = start; i < Math.min(start + BATCH, USER_COUNT); i++) {
            requests.push(['POST', `${BASE_URL}/api/auth/signup`,
                JSON.stringify({ email: email(i), password: PASSWORD, nickname: `k6user${i}` }), JSON_HEADERS]);
        }
        http.batch(requests);
    }
}

export default function () {
    const i = Math.floor(Math.random() * USER_COUNT);
    const body = SCENARIO === 'unknown'
        ? { email: `k6-nouser-${i}@example.com`, password: PASSWORD }
        : { email: email(i), password: PASSWORD };
    const expected = SCENARIO === 'unknown' ? 404 : 200;

    const res = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify(body),
        Object.assign({ tags: { name: 'login' } }, JSON_HEADERS));
    check(res, { [`status ${expected}`]: (r) => r.status === expected });

    // status 0 = 응답을 받지 못함 (연결 거부/리셋, 타임아웃 등). error_code로 원인을 남긴다
    const result = res.status === expected ? 'ok' : (res.status === 0 ? 'network_error' : 'http_error');
    loginResult.add(1, { result, status: String(res.status), error_code: String(res.error_code || 0) });
}
