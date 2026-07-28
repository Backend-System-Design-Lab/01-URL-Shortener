import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
    stages: [
        { duration: '20s', target: 10 },
        { duration: '40s', target: 20 },
        { duration: '20s', target: 0 },
    ],

    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: [
            'p(95)<200',
            'p(99)<500',
        ],
        checks: ['rate>0.99'],
    },
};

export default function () {
    const response = http.get(`${BASE_URL}/api/v1/ping`, {
        tags: {
            endpoint: 'ping',
            test_type: 'load',
        },
    });

    check(response, {
        'status is 200': (res) => res.status === 200,
        'message is pong': (res) => {
            try {
                return res.json().message === 'pong';
            } catch {
                return false;
            }
        },
    });

    sleep(0.2);
}