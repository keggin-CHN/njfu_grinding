#!/usr/bin/env python3
import argparse
import hashlib
import json
import os
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, quote, unquote, urlparse
import urllib.error
import urllib.request

REPO_OWNER = os.getenv("GITHUB_OWNER", "keggin-CHN")
REPO_NAME = os.getenv("GITHUB_REPO", "njfu_grinding")
REPO_BRANCH = os.getenv("GITHUB_BRANCH", "main")
BASE_PATH = os.getenv("GITHUB_BASE_PATH", "题库收集")

CACHE_DIR = os.getenv("CACHE_DIR", "cache")
API_TTL = int(os.getenv("API_TTL", "300"))
RAW_TTL = int(os.getenv("RAW_TTL", "86400"))
TIMEOUT_SECONDS = int(os.getenv("UPSTREAM_TIMEOUT", "10"))
GITHUB_TOKEN = os.getenv("GITHUB_TOKEN", "")
USER_AGENT = os.getenv("USER_AGENT", "njfu-github-proxy/1.0")


def ensure_cache_dir():
    os.makedirs(CACHE_DIR, exist_ok=True)


def encode_path(path):
    segments = [s for s in path.split("/") if s]
    return "/".join(quote(s, safe="") for s in segments)


def build_api_url(path):
    encoded = encode_path(path)
    return f"https://api.github.com/repos/{REPO_OWNER}/{REPO_NAME}/contents/{encoded}"


def build_raw_url(path):
    encoded = encode_path(path)
    return f"https://raw.githubusercontent.com/{REPO_OWNER}/{REPO_NAME}/{REPO_BRANCH}/{encoded}"


def cache_paths(url):
    key = hashlib.sha256(url.encode("utf-8")).hexdigest()
    return (
        os.path.join(CACHE_DIR, f"{key}.bin"),
        os.path.join(CACHE_DIR, f"{key}.json"),
    )


def load_cache(url, ttl):
    data_path, meta_path = cache_paths(url)
    if not (os.path.exists(data_path) and os.path.exists(meta_path)):
        return None
    try:
        with open(meta_path, "r", encoding="utf-8") as meta_file:
            meta = json.load(meta_file)
        if time.time() - meta.get("timestamp", 0) > ttl:
            return None
        with open(data_path, "rb") as data_file:
            body = data_file.read()
        return meta.get("status", 200), meta.get("content_type", "application/octet-stream"), body
    except Exception:
        return None


def save_cache(url, status, content_type, body):
    data_path, meta_path = cache_paths(url)
    try:
        with open(data_path, "wb") as data_file:
            data_file.write(body)
        with open(meta_path, "w", encoding="utf-8") as meta_file:
            json.dump(
                {
                    "timestamp": time.time(),
                    "status": status,
                    "content_type": content_type,
                },
                meta_file,
            )
    except Exception:
        pass


def build_headers(is_api):
    headers = {"User-Agent": USER_AGENT}
    if GITHUB_TOKEN:
        headers["Authorization"] = f"Bearer {GITHUB_TOKEN}"
    if is_api:
        headers["Accept"] = "application/vnd.github+json"
    return headers


def request_upstream(url, headers):
    request = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            status = response.getcode()
            content_type = response.headers.get("Content-Type", "application/octet-stream")
            body = response.read()
            return status, content_type, body
    except urllib.error.HTTPError as exc:
        content_type = "text/plain; charset=utf-8"
        if exc.headers:
            content_type = exc.headers.get("Content-Type", content_type)
        body = exc.read() if exc.fp else str(exc).encode("utf-8")
        return exc.code, content_type, body


def fetch_with_cache(url, ttl, headers):
    cached = load_cache(url, ttl)
    if cached:
        status, content_type, body = cached
        return status, content_type, body, True
    status, content_type, body = request_upstream(url, headers)
    if status == 200:
        save_cache(url, status, content_type, body)
    return status, content_type, body, False


class GitHubProxyHandler(BaseHTTPRequestHandler):
    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.end_headers()

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            self._write_response(200, "text/plain; charset=utf-8", b"ok", False)
            return
        if parsed.path.startswith("/api/contents"):
            self._handle_api(parsed)
            return
        if parsed.path.startswith("/raw"):
            self._handle_raw(parsed)
            return
        self._write_response(404, "text/plain; charset=utf-8", b"Not Found", False)

    def _resolve_path(self, parsed, prefix):
        remainder = parsed.path[len(prefix):].lstrip("/")
        if remainder:
            return unquote(remainder)
        query = parse_qs(parsed.query)
        if "path" in query:
            return query["path"][0]
        return ""

    def _handle_api(self, parsed):
        path_value = self._resolve_path(parsed, "/api/contents")
        if not path_value:
            path_value = BASE_PATH
        path_value = path_value.strip("/")
        if not path_value:
            self._write_response(400, "text/plain; charset=utf-8", b"path required", False)
            return
        url = build_api_url(path_value)
        status, content_type, body, from_cache = fetch_with_cache(url, API_TTL, build_headers(True))
        self._write_response(status, content_type, body, from_cache)

    def _handle_raw(self, parsed):
        path_value = self._resolve_path(parsed, "/raw")
        path_value = path_value.strip("/")
        if not path_value:
            self._write_response(400, "text/plain; charset=utf-8", b"path required", False)
            return
        url = build_raw_url(path_value)
        status, content_type, body, from_cache = fetch_with_cache(url, RAW_TTL, build_headers(False))
        self._write_response(status, content_type, body, from_cache)

    def _write_response(self, status, content_type, body, from_cache):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("X-Cache", "HIT" if from_cache else "MISS")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        return


def parse_args():
    parser = argparse.ArgumentParser(description="Simple GitHub proxy with caching")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=16974)
    parser.add_argument("--owner", default=REPO_OWNER)
    parser.add_argument("--repo", default=REPO_NAME)
    parser.add_argument("--branch", default=REPO_BRANCH)
    parser.add_argument("--base-path", default=BASE_PATH)
    return parser.parse_args()


def main():
    global REPO_OWNER, REPO_NAME, REPO_BRANCH, BASE_PATH
    args = parse_args()
    REPO_OWNER = args.owner
    REPO_NAME = args.repo
    REPO_BRANCH = args.branch
    BASE_PATH = args.base_path
    ensure_cache_dir()
    server = ThreadingHTTPServer((args.host, args.port), GitHubProxyHandler)
    print(f"GitHub proxy running on {args.host}:{args.port}")
    server.serve_forever()


if __name__ == "__main__":
    main()