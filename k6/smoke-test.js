import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
    vus: 1,
    duration: '10s',

    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<500'],
        checks: ['rate>0.99'],
    },
};

export default function () {
    const response = http.get(`${BASE_URL}/api/v1/ping`, {
        tags: {
            endpoint: 'ping',
            test_type: 'smoke',
        },
    });

    check(response, {
        'status is 200': (res) => res.status === 200,
        'content type is JSON': (res) =>
            res.headers['Content-Type']?.includes('application/json'),
        'message is pong': (res) => {
            try {
                return res.json().message === 'pong';
            } catch {
                return false;
            }
        },
    });

    sleep(1);
}