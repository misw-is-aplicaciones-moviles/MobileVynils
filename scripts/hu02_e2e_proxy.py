#!/usr/bin/env python3
"""Proxy local de HU02-05: solo GET de álbumes; nunca modifica BackVynils."""
import argparse
import json
import re
import socket
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen

CONTROL = "/__hu02_e2e__/"


class AlbumProxy(BaseHTTPRequestHandler):
    def reply(self, status, payload):
        body = payload if isinstance(payload, bytes) else json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        action = self.path.removeprefix(CONTROL)
        with self.server.state_lock:
            if self.path == CONTROL + "reset":
                self.server.next_failure = None
                self.server.detail_requests = 0
            elif self.path.startswith(CONTROL + "next/") and action[5:] in {"404", "503", "disconnect"}:
                self.server.next_failure = action[5:]
            else:
                self.reply(404, {"error": "Acción de prueba desconocida"})
                return
        self.reply(200, {"ok": True})

    def do_GET(self):
        path = urlsplit(self.path).path
        if path == CONTROL + "stats":
            with self.server.state_lock:
                count = self.server.detail_requests
            self.reply(200, {"detailRequests": count})
            return
        if not re.fullmatch(r"/albums(?:/\d+)?", path):
            self.reply(404, {"error": "El proxy solo admite consultas de álbumes"})
            return
        failure = None
        if path.startswith("/albums/"):
            with self.server.state_lock:
                self.server.detail_requests += 1
                failure = self.server.next_failure
                self.server.next_failure = None
        if failure == "disconnect":
            self.close_connection = True
            try:
                self.connection.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            self.connection.close()
            return
        if failure:
            self.reply(int(failure), {"statusCode": int(failure), "message": "Fallo simulado de HU02"})
            return
        headers = {"Accept": "application/json"}
        if self.headers.get("Cache-Control"):
            headers["Cache-Control"] = self.headers["Cache-Control"]
        request = Request(self.server.backend + self.path, headers=headers)
        try:
            with urlopen(request, timeout=10) as response:
                self.reply(response.status, response.read())
        except HTTPError as error:
            with error:
                self.reply(error.code, error.read())
        except URLError:
            self.reply(502, {"error": "BackVynils no está disponible"})

    def log_message(self, format, *args):
        pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=3301)
    parser.add_argument("--backend", default="http://127.0.0.1:3000")
    args = parser.parse_args()
    server = ThreadingHTTPServer(("127.0.0.1", args.port), AlbumProxy)
    server.backend = args.backend.rstrip("/")
    server.state_lock = threading.Lock()
    server.next_failure = None
    server.detail_requests = 0
    print(f"Proxy HU02 en http://127.0.0.1:{server.server_port}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
