# 本地开发服务器：静态文件 + /api/ 代理到服务器后端（经 SSH 隧道 127.0.0.1:18080）
# 用法：先建立隧道  ssh -N -L 18080:127.0.0.1:8080 root@180.184.160.179
#       再启动      python serve.py [端口，默认 8791]
import http.server
import socketserver
import sys
import urllib.request
import urllib.error

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8791
API_TARGET = "http://127.0.0.1:18080"
SANDBOX_TARGET = "http://127.0.0.1:8090"


class Handler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith("/sandbox/api/"):
            self._proxy(SANDBOX_TARGET)
        elif self.path.startswith("/api/"):
            self._proxy(API_TARGET)
        else:
            super().do_GET()

    def do_POST(self):
        if self.path.startswith("/sandbox/api/"):
            self._proxy(SANDBOX_TARGET)
        elif self.path.startswith("/api/"):
            self._proxy(API_TARGET)
        else:
            self.send_error(404)

    def _proxy(self, target):
        url = target + self.path
        try:
            body = None
            if self.command == "POST":
                length = int(self.headers.get("Content-Length") or 0)
                body = self.rfile.read(length) if length else b""
            req = urllib.request.Request(url, data=body, method=self.command)
            req.add_header("Accept", "application/json")
            if body is not None:
                req.add_header("Content-Type", self.headers.get("Content-Type") or "application/json")
            with urllib.request.urlopen(req, timeout=15) as resp:
                payload = resp.read()
                self.send_response(resp.status)
                self.send_header("Content-Type", resp.headers.get("Content-Type", "application/json"))
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)
        except urllib.error.HTTPError as e:
            payload = e.read()
            self.send_response(e.code)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
        except Exception as e:
            payload = ('{"code":502,"message":"local proxy error: %s"}' % e).encode()
            self.send_response(502)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

    def log_message(self, fmt, *args):
        pass


socketserver.ThreadingTCPServer.allow_reuse_address = True
with socketserver.ThreadingTCPServer(("127.0.0.1", PORT), Handler) as httpd:
    print("serving http://127.0.0.1:%d/  (api -> %s)" % (PORT, API_TARGET))
    httpd.serve_forever()
