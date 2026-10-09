"""The helper of claude-overhead.sh: what one `claude -p` launch sends before the model reads a word.

    claude_overhead.py serve <port-file> <out-dir> [<mcp-tools.json>]
                                                       a stand-in for the Anthropic Messages API on loopback,
                                                       and with a tool list a stand-in MCP server at /mcp
    claude_overhead.py measure <request.json> <stream.jsonl>
    claude_overhead.py defaults <reference.conf>       the `agents.providers.claude-code.env` defaults, K=V
    claude_overhead.py first-call <stream.jsonl>       a LIVE run's first model call: input + cache tokens
    claude_overhead.py table <label>=<measure.json> …  the arms side by side

`serve` records each request BODY it is sent (never a header: the key the script passes is a dummy)
and answers every message with "ok" and end_turn, so the CLI finishes without any model call. Given a
`{"tools": [...]}` file (what `PromptBudget export` writes per role), it also answers MCP's
`initialize` and `tools/list` at `/mcp` with those tools, so the role's MCP tools reach the request
as Claude Code renders them; a tool is never called. Its
sizes are characters of the JSON the CLI sent; `tokens` is an estimate (characters / 4), not a count.
"""
import http.server
import itertools
import json
import os
import re
import sys

# ── the stand-in API ──────────────────────────────────────────────────────────────────────────────


def _message(i, model):
    return {"id": f"msg_overhead_{i}", "type": "message", "role": "assistant", "model": model,
            "content": [{"type": "text", "text": "ok"}], "stop_reason": "end_turn", "stop_sequence": None,
            "usage": {"input_tokens": 1, "output_tokens": 1, "cache_creation_input_tokens": 0,
                      "cache_read_input_tokens": 0}}


MCP_PROTOCOL = "2025-06-18"


def mcp_answer(req, tools):
    """The stand-in MCP server's reply to one JSON-RPC message: None for a notification (202, no body)."""
    if "id" not in req:
        return None
    method = req.get("method")
    if method == "initialize":
        version = (req.get("params") or {}).get("protocolVersion") or MCP_PROTOCOL
        result = {"protocolVersion": version, "capabilities": {"tools": {"listChanged": False}},
                  "serverInfo": {"name": "histmersion-overhead", "version": "0"}}
    elif method == "tools/list":
        result = {"tools": tools}
    elif method == "ping":
        result = {}
    else:
        return {"jsonrpc": "2.0", "id": req["id"], "error": {"code": -32601, "message": f"no {method} here"}}
    return {"jsonrpc": "2.0", "id": req["id"], "result": result}


def handler(out_dir, tools=None):
    seq = itertools.count(1)

    class Handler(http.server.BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def _log(self, line):
            with open(os.path.join(out_dir, "paths.log"), "a", encoding="utf-8") as f:
                f.write(line + "\n")

        def do_GET(self):
            self._log("GET " + self.path)
            self.send_response(405 if self.path.startswith("/mcp") else 404)
            self.end_headers()

        def do_DELETE(self):
            self.send_response(204)
            self.end_headers()

        def _mcp(self, raw):
            answer = mcp_answer(json.loads(raw or b"{}"), tools or [])
            if answer is None:
                self.send_response(202)
                self.end_headers()
                return
            body = json.dumps(answer).encode()
            self.send_response(200)
            self.send_header("content-type", "application/json")
            self.send_header("content-length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_POST(self):
            n = int(self.headers.get("content-length") or 0)
            raw = self.rfile.read(n) if n else b""
            self._log("POST " + self.path)
            if tools is not None and self.path.startswith("/mcp"):
                self._mcp(raw)
                return
            if not self.path.startswith("/v1/messages") or "count_tokens" in self.path:
                self.send_response(404)
                self.end_headers()
                return
            i = next(seq)
            with open(os.path.join(out_dir, f"request-{i:03d}.json"), "wb") as f:
                f.write(raw)
            req = json.loads(raw or b"{}")
            msg = _message(i, req.get("model", "unknown"))
            if req.get("stream"):
                self.send_response(200)
                self.send_header("content-type", "text/event-stream")
                self.end_headers()

                def event(kind, data):
                    self.wfile.write(f"event: {kind}\ndata: {json.dumps(data)}\n\n".encode())

                event("message_start", {"type": "message_start", "message": dict(msg, content=[], stop_reason=None)})
                event("content_block_start", {"type": "content_block_start", "index": 0,
                                              "content_block": {"type": "text", "text": ""}})
                event("content_block_delta", {"type": "content_block_delta", "index": 0,
                                              "delta": {"type": "text_delta", "text": "ok"}})
                event("content_block_stop", {"type": "content_block_stop", "index": 0})
                event("message_delta", {"type": "message_delta", "usage": {"output_tokens": 1},
                                        "delta": {"stop_reason": "end_turn", "stop_sequence": None}})
                event("message_stop", {"type": "message_stop"})
            else:
                body = json.dumps(msg).encode()
                self.send_response(200)
                self.send_header("content-type", "application/json")
                self.send_header("content-length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

    return Handler


def serve(port_file, out_dir, tools_file=None):
    os.makedirs(out_dir, exist_ok=True)
    tools = None
    if tools_file:
        with open(tools_file, encoding="utf-8") as f:
            tools = json.load(f).get("tools") or []
    srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler(out_dir, tools))
    with open(port_file, "w", encoding="utf-8") as f:
        f.write(str(srv.server_address[1]))
    srv.serve_forever()


# ── reading a launch ──────────────────────────────────────────────────────────────────────────────


def _chars(x):
    return len(json.dumps(x, ensure_ascii=False))


def measure(request, init):
    """The sizes of one request body, and what the CLI's init event says it loaded."""
    tools = request.get("tools") or []
    system = request.get("system") or []
    messages = request.get("messages") or []
    return {
        "system": _chars(system),
        "tools": _chars(tools),
        "tool_names": [t.get("name", "?") for t in tools],
        "mcp_tools": _chars([t for t in tools if str(t.get("name", "")).startswith("mcp__")]),
        "messages": _chars(messages),
        "total": _chars(system) + _chars(tools) + _chars(messages),
        "init_tools": len(init.get("tools") or []),
        "init_skills": len(init.get("skills") or []),
        "init_slash_commands": len(init.get("slash_commands") or []),
        "init_agents": len(init.get("agents") or []),
    }


def events(lines):
    for line in lines:
        line = line.strip()
        if line.startswith("{"):
            try:
                yield json.loads(line)
            except ValueError:
                pass


def init_event(lines):
    return next((e for e in events(lines) if e.get("type") == "system" and e.get("subtype") == "init"), {})


def first_call(lines):
    """The first top-level assistant event's usage: input + cache read + cache write tokens (what the
    server stores as agent_steps.first_call_tokens). None when there is none."""
    for e in events(lines):
        if e.get("type") != "assistant" or e.get("parent_tool_use_id") is not None:
            continue
        msg = e.get("message") or {}
        if msg.get("model") == "<synthetic>":
            continue
        u = msg.get("usage") or {}
        n = sum(int(u.get(k) or 0) for k in ("input_tokens", "cache_read_input_tokens", "cache_creation_input_tokens"))
        if n > 0:
            return n
    return None


_BLOCK = re.compile(r"^\s*claude-code\s*\{")
_ENTRY = re.compile(r'^\s*([A-Z][A-Z0-9_]*)\s*=\s*"([^"]*)"')


def defaults(text):
    """The variables reference.conf sets in `agents.providers.claude-code.env { … }`, in order."""
    out, in_cc, in_env = [], False, False
    for line in text.splitlines():
        if not in_cc:
            in_cc = bool(_BLOCK.match(line))
            continue
        if not in_env:
            if re.match(r"^\s*env\s*\{", line):
                in_env = True
            elif re.match(r"^\s{6}\}", line):  # the end of the claude-code block
                break
            continue
        if re.match(r"^\s*\}", line):
            break
        m = _ENTRY.match(line)
        if m:
            out.append((m.group(1), m.group(2)))
    return out


def table(rows):
    """`rows`: (label, measure) pairs, the first the baseline. Characters, with the change against it."""
    base = rows[0][1]
    head = f"{'arm':44} {'system':>8} {'tools':>8} {'(mcp)':>8} {'messages':>9} {'total':>8} {'~tokens':>8} " \
           f"{'vs ' + rows[0][0]:>12}  init: tools skills slash agents"
    lines = [head, "-" * len(head)]
    for label, m in rows:
        delta = m["total"] - base["total"]
        lines.append(f"{label[:44]:44} {m['system']:>8} {m['tools']:>8} {m.get('mcp_tools', 0):>8} {m['messages']:>9} "
                     f"{m['total']:>8} {m['total'] // 4:>8} {delta:>+12}  {m['init_tools']:>11} {m['init_skills']:>6} "
                     f"{m['init_slash_commands']:>5} {m['init_agents']:>6}")
    return "\n".join(lines)


def main(argv):
    cmd = argv[1] if len(argv) > 1 else ""
    if cmd == "serve" and len(argv) in (4, 5):
        serve(argv[2], argv[3], argv[4] if len(argv) == 5 else None)
    elif cmd == "measure" and len(argv) == 4:
        with open(argv[2], encoding="utf-8") as r, open(argv[3], encoding="utf-8") as s:
            print(json.dumps(measure(json.load(r), init_event(s))))
    elif cmd == "defaults" and len(argv) == 3:
        with open(argv[2], encoding="utf-8") as f:
            for k, v in defaults(f.read()):
                print(f"{k}={v}")
    elif cmd == "first-call" and len(argv) == 3:
        with open(argv[2], encoding="utf-8") as f:
            n = first_call(f)
        print("" if n is None else n)
    elif cmd == "table" and len(argv) >= 3:
        rows = []
        for a in argv[2:]:
            label, path = a.split("=", 1)
            with open(path, encoding="utf-8") as f:
                rows.append((label, json.load(f)))
        print(table(rows))
    else:
        print(__doc__, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
