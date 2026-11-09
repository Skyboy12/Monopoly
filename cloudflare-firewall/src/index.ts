/**
 * Cloudflare Worker - Edge Firewall & Reverse Proxy for Monopoly Webgame
 *
 * Chức năng:
 * 1. CORS Preflight handling
 * 2. Method Whitelisting (GET, POST, OPTIONS, Upgrade WebSocket)
 * 3. Path Whitelisting & Scanner Drop (.php, .env, ../, wp-admin)
 * 4. User-Agent malicious detection
 * 5. Content-Length payload protection (<= 100KB)
 * 6. Content-Type enforcement for POST requests (application/json)
 * 7. Pre-validation JWT Token format
 * 8. WebSocket upgrade forwarding
 * 9. Origin Shielding injection (X-Origin-Verify)
 */

export interface Env {
  ORIGIN_URL: string;
  ORIGIN_SECRET_KEY?: string;
}

const MALICIOUS_PATH_REGEX = /\.(php|asp|aspx|jsp|env|git|yml|yaml|config|ini|sh|bak|swp)|wp-admin|phpmyadmin|cgi-bin|\/\.\./i;
const MALICIOUS_UA_REGEX = /(sqlmap|nikto|nmap|masscan|zgrab|acunetix|gobuster|dirbuster)/i;
const ALLOWED_METHODS = new Set(['GET', 'POST', 'OPTIONS']);
const JWT_FORMAT_REGEX = /^Bearer\s+[A-Za-z0-9-_]+\.[A-Za-z0-9-_]+\.[A-Za-z0-9-_]+$/;

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);
    const path = url.pathname;
    const method = request.method.toUpperCase();

    // 1. Xử lý CORS Preflight (OPTIONS)
    if (method === 'OPTIONS') {
      return handleCorsPreflight(request);
    }

    // 2. Lọc HTTP Method (Method Whitelisting)
    if (!ALLOWED_METHODS.has(method)) {
      return new Response(JSON.stringify({ error: 'METHOD_NOT_ALLOWED', message: `Method ${method} is not allowed` }), {
        status: 405,
        headers: {
          'Content-Type': 'application/json',
          'Allow': 'GET, POST, OPTIONS',
          'Access-Control-Allow-Origin': '*',
        },
      });
    }

    // 3. Quét phát hiện mã độc & đường dẫn rác (Scanner Drop)
    if (MALICIOUS_PATH_REGEX.test(path)) {
      console.warn(`[FIREWALL DROP] Blocked suspicious path: ${path} from IP: ${request.headers.get('CF-Connecting-IP')}`);
      return new Response('Not Found', { status: 404 });
    }

    // 4. Kiểm tra User-Agent
    const userAgent = request.headers.get('User-Agent') || '';
    if (!userAgent || MALICIOUS_UA_REGEX.test(userAgent)) {
      return new Response('Forbidden', { status: 403 });
    }

    // 5. Kiểm tra tiền tố Path hợp lệ (Path Whitelisting)
    const isApi = path.startsWith('/api/');
    const isWs = path.startsWith('/ws') || path.startsWith('/socket.io');
    const isHealth = path === '/health';

    if (!isApi && !isWs && !isHealth && path !== '/') {
      return new Response('Route not found', { status: 404 });
    }

    // Health check endpoint tại Edge
    if (isHealth) {
      return new Response(JSON.stringify({ status: 'UP', firewall: 'ACTIVE', timestamp: Date.now() }), {
        status: 200,
        headers: {
          'Content-Type': 'application/json',
          'Access-Control-Allow-Origin': '*',
        },
      });
    }

    // 6. Kiểm tra giới hạn kích thước Payload (Content-Length <= 100 KB)
    const contentLength = parseInt(request.headers.get('Content-Length') || '0', 10);
    if (contentLength > 102400) {
      return new Response(JSON.stringify({ error: 'PAYLOAD_TOO_LARGE', message: 'Kích thước dữ liệu vượt quá 100KB' }), {
        status: 413,
        headers: {
          'Content-Type': 'application/json',
          'Access-Control-Allow-Origin': '*',
        },
      });
    }

    // 7. Bắt buộc Content-Type application/json cho POST API
    if (isApi && method === 'POST') {
      const contentType = request.headers.get('Content-Type') || '';
      if (!contentType.toLowerCase().includes('application/json')) {
        return new Response(JSON.stringify({ error: 'UNSUPPORTED_MEDIA_TYPE', message: 'Content-Type phải là application/json' }), {
          status: 415,
          headers: {
            'Content-Type': 'application/json',
            'Access-Control-Allow-Origin': '*',
          },
        });
      }
    }

    // 8. Pre-validation JWT Token tại Edge cho các route cần xác thực
    const isProtectedApi = path.startsWith('/api/rooms') || path.startsWith('/api/game') || path.startsWith('/api/v1/rooms');
    if (isProtectedApi) {
      const authHeader = request.headers.get('Authorization');
      if (!authHeader || !JWT_FORMAT_REGEX.test(authHeader)) {
        return new Response(JSON.stringify({ error: 'UNAUTHORIZED', message: 'Token xác thực không hợp lệ hoặc bị thiếu' }), {
          status: 401,
          headers: {
            'Content-Type': 'application/json',
            'Access-Control-Allow-Origin': '*',
          },
        });
      }
    }

    // 9. Xử lý WebSocket Handshake
    if (isWs) {
      const upgradeHeader = request.headers.get('Upgrade');
      if (!upgradeHeader || upgradeHeader.toLowerCase() !== 'websocket') {
        return new Response('Expected WebSocket upgrade', { status: 426 });
      }
    }

    // 10. Chuyển tiếp Request tới Origin kèm Secret Header (Origin Shielding)
    const originBase = env.ORIGIN_URL || 'http://localhost:8080';
    const originUrl = new URL(path + url.search, originBase);
    const newHeaders = new Headers(request.headers);

    if (env.ORIGIN_SECRET_KEY) {
      newHeaders.set('X-Origin-Verify', env.ORIGIN_SECRET_KEY);
    }
    newHeaders.set('X-Forwarded-For', request.headers.get('CF-Connecting-IP') || '');
    newHeaders.set('X-Forwarded-Proto', 'https');

    try {
      const originResponse = await fetch(originUrl.toString(), {
        method: request.method,
        headers: newHeaders,
        body: request.body,
        redirect: 'manual',
      });

      const responseHeaders = new Headers(originResponse.headers);
      responseHeaders.set('Access-Control-Allow-Origin', '*');
      responseHeaders.set('X-Edge-Protected-By', 'Cloudflare-Worker-Firewall');

      return new Response(originResponse.body, {
        status: originResponse.status,
        statusText: originResponse.statusText,
        headers: responseHeaders,
      });
    } catch (err: any) {
      console.error(`[ORIGIN ERROR] Failed to connect to origin: ${err.message}`);
      return new Response(JSON.stringify({ error: 'BAD_GATEWAY', message: 'Không thể kết nối đến máy chủ game origin' }), {
        status: 502,
        headers: {
          'Content-Type': 'application/json',
          'Access-Control-Allow-Origin': '*',
        },
      });
    }
  },
};

function handleCorsPreflight(request: Request): Response {
  return new Response(null, {
    status: 204,
    headers: {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Requested-With',
      'Access-Control-Max-Age': '86400',
    },
  });
}
