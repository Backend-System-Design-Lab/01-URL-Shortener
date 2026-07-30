import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://app:8080';

export const options = {
    vus: Number(__ENV.VUS || 20),
    duration: __ENV.DURATION || '1m',
    maxRedirects: 0,

    summaryTrendStats: [
        'avg',
        'min',
        'med',
        'p(90)',
        'p(95)',
        'p(99)',
        'max',
    ],

    thresholds: {
        checks: ['rate>0.99'],
        http_req_failed: ['rate<0.01'],
        'http_req_duration{endpoint:redirect}': ['p(95)<500']
    }
};

export function setup() {
    const response = http.post(
        `${BASE_URL}/api/v1/data/shorten`,
        JSON.stringify({
           longUrl: 'https://www.google.com',
        }),
        {
            headers: {
                'Content-Type': 'application/json',
            },
            tags: {
                endpoint: 'setup',
            }
        }
    );

    const success = check(response, {
        '단축 URL 생성 상태는 201이다': (res) => res.status === 201,
        'shortCode가 반환된다': (res) => {
            const body = res.json();
            return body.shortCode !== undefined && body.shortCode !== null;
        }
    });

    if (!success) {
        throw new Error(
            `단축 URL 생성 실패: status=${response.status}, body=${response.body}`
        );
    }

    return {
        shortCode: response.json().shortCode,
    };
}

export default function (data) {
    const response = http.get(
        `${BASE_URL}/api/v1/${data.shortCode}`,
        {
            redirects: 0,
            tags: {
                endpoint: 'redirect',
            }
        }
    );

    check(response, {
        '리다이렉트 상태는 302이다': (res) => res.status === 302,
        'Location 헤더가 존재한다': (res) =>
            res.headers.Location !== undefined,
        '원본 URL이 올바르다': (res) =>
            res.headers.Location === 'https://www.google.com'
    });
}