import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SHORT_CODE = __ENV.SHORT_CODE;

if (!SHORT_CODE) {
    throw new Error('SHORT_CODE environment variable is required');
}

const upstreamRetry = new Counter('nginx_upstream_retry');

export const options = {
    vus: Number(__ENV.VUS || 100),
    duration: __ENV.DURATION || '120s',

    thresholds: {
        http_req_failed: ['rate<0.01'],
        checks: ['rate>0.99'],
    },
};

export default function () {
    const response = http.get(
        `${BASE_URL}/api/v1/${SHORT_CODE}`,
        {
            redirects: 0,
            tags: {
                experiment: 'app-failover',
            },
        }
    );

    check(response, {
        'redirect status is 302': (r) => r.status === 302,
        'location exists': (r) =>
            typeof r.headers.Location === 'string' &&
            r.headers.Location.length > 0,
    });

    const upstream = response.headers['X-Upstream-Addr'];

    // Nginx가 첫 upstream 실패 후 다른 upstream으로 재시도한 경우
    if (upstream && upstream.includes(',')) {
        upstreamRetry.add(1);
    }
}