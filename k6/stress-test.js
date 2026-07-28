import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
    stages: [
        { duration: '30s', target: 20 },
        { duration: '30s', target: 50 },
        { duration: '30s', target: 100 },
        { duration: '30s', target: 200 },
        { duration: '20s', target: 0 },
    ],

    thresholds: {
        http_req_failed: ['rate<0.10'],
        checks: ['rate>0.90'],
    },
};

export default function () {
    const response = http.get(`${BASE_URL}/api/v1/ping`, {
        tags: {
            endpoint: 'ping',
            test_type: 'stress',
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