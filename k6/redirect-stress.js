import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://app:8080';

export const options = {
    stages: [
        { duration: '20s', target: 100 },
        { duration: '40s', target: 100 },

        { duration: '20s', target: 200 },
        { duration: '40s', target: 200 },

        { duration: '20s', target: 300 },
        { duration: '40s', target: 300 },

        { duration: '20s', target: 500 },
        { duration: '40s', target: 500 },

        { duration: '20s', target: 0 },
    ],

    thresholds: {
        'http_req_failed{endpoint:redirect}': ['rate<0.10'],
        'checks{endpoint:redirect}': ['rate>0.90'],
    },
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
            },
        }
    );

    const success = check(
        response,
        {
            '단축 URL 생성 상태는 201이다': (res) => res.status === 201,
            'shortCode가 반환된다': (res) => {
                try {
                    const body = res.json();
                    return body.shortCode !== undefined
                        && body.shortCode !== null;
                } catch {
                    return false;
                }
            },
        },
        {
            endpoint: 'setup',
        }
    );

    if (!success) {
        throw new Error(
            `단축 URL 생성 실패: status=${response.status}, body=${response.body}`
        );
    }

    const shortCode = response.json().shortCode;

    // Redis에서는 캐시 Warm-up, DB Only에서는 초기 동작 확인 역할
    const initialRedirectResponse = http.get(
        `${BASE_URL}/api/v1/${shortCode}`,
        {
            redirects: 0,
            tags: {
                endpoint: 'warm-up',
            },
        }
    );

    if (initialRedirectResponse.status !== 302) {
        throw new Error(
            `초기 리다이렉트 실패: status=${initialRedirectResponse.status}`
        );
    }

    return {
        shortCode,
    };
}

export default function (data) {
    const response = http.get(
        `${BASE_URL}/api/v1/${data.shortCode}`,
        {
            redirects: 0,
            tags: {
                endpoint: 'redirect',
                test_type: 'stress',
            },
        }
    );

    check(
        response,
        {
            '리다이렉트 상태는 302이다': (res) => res.status === 302,
            'Location 헤더가 존재한다': (res) =>
                res.headers.Location !== undefined,
            '원본 URL이 올바르다': (res) =>
                res.headers.Location === 'https://www.google.com',
        },
        {
            endpoint: 'redirect',
        }
    );
}