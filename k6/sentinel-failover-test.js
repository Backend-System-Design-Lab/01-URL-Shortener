import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SHORT_CODE = __ENV.SHORT_CODE;

export const options = {
    scenarios: {
        sentinel_failover: {
            executor: 'constant-vus',
            vus: Number(__ENV.VUS || 100),
            duration: __ENV.DURATION || '120s',
        },
    },
};

export default function () {
    const response = http.get(
        `${BASE_URL}/api/v1/${SHORT_CODE}`,
        {
            redirects: 0,
            tags: {
                name: 'redirect',
            },
        }
    );

    check(response, {
        'redirect status is 302': (res) => res.status === 302,
    });
}