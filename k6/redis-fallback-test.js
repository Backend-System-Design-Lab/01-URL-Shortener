import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SHORT_CODE = __ENV.SHORT_CODE;

if (!SHORT_CODE) {
    throw new Error('SHORT_CODE 환경변수가 필요합니다.');
}

export const options = {
    vus: Number(__ENV.VUS || 100),
    duration: __ENV.DURATION || '90s',

    thresholds: {
        http_req_failed: ['rate<0.01'],
        checks: ['rate>0.99'],
        http_req_duration: ['p(95)<1000'],
    },
};

export default function () {
    const response = http.get(
        `${BASE_URL}/api/v1/${SHORT_CODE}`,
        {
            redirects: 0,
            tags: {
                experiment: 'redis-fallback',
            },
        },
    );

    check(response, {
        'redirect status is 302': (res) => res.status === 302,
        'location header exists': (res) =>
            typeof res.headers.Location === 'string'
            && res.headers.Location.length > 0,
    });
}