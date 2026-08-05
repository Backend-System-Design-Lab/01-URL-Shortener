import http from 'k6/http';
import { check } from 'k6';

export const options = {
    vus: Number(__ENV.VUS || 20),
    duration: __ENV.DURATION || '1m',

    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<1000'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const RUN_ID = __ENV.RUN_ID || 'local';

export default function () {
    /*
     * Hash 방식에서 같은 URL을 반복 요청하면
     * 기존 코드를 반환하는 조회 경로가 실행된다.
     *
     * 실제 생성 성능을 비교하기 위해 요청마다
     * 서로 다른 longUrl을 사용한다.
     */
    const longUrl = `https://example.com/${RUN_ID}/${__VU}/${__ITER}`;

    const response = http.post(
        `${BASE_URL}/api/v1/data/shorten`,
        JSON.stringify({
            longUrl: longUrl,
        }),
        {
            headers: {
                'Content-Type': 'application/json',
            },
        }
    );

    check(response, {
        'shorten status is 2xx': (res) =>
            res.status >= 200 && res.status < 300,
    });
}