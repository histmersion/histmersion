"""Tests of claude_overhead.py (claude-overhead.sh's helper) — no claude, no network beyond loopback.

    python3 -m unittest scripts/agents/tests/test_claude_overhead.py      (also: just test-scripts)
"""
import http.client
import json
import os
import sys
import tempfile
import threading
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import claude_overhead as co  # noqa: E402

REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".."))

# 2.1.294's stream-json, as an offline run of claude-overhead.sh recorded it (trimmed)
INIT = {"type": "system", "subtype": "init", "tools": ["StructuredOutput"], "skills": ["a", "b"],
        "slash_commands": ["a", "b", "c"], "agents": ["claude", "Explore", "Plan"]}


def assistant(msg_id, usage, model="claude-sonnet-4-5", parent=None):
    return json.dumps({"type": "assistant", "parent_tool_use_id": parent,
                       "message": {"id": msg_id, "model": model, "content": [{"type": "text", "text": "ok"}],
                                   "usage": usage}})


class MeasureTest(unittest.TestCase):
    def test_sizes_of_system_tools_and_messages_and_the_init_counts(self):
        req = {"system": [{"type": "text", "text": "x" * 100}], "tools": [{"name": "StructuredOutput"}],
               "messages": [{"role": "user", "content": "hi"}]}
        m = co.measure(req, INIT)
        self.assertEqual(m["system"], len(json.dumps(req["system"])))
        self.assertEqual(m["tool_names"], ["StructuredOutput"])
        self.assertEqual(m["total"], m["system"] + m["tools"] + m["messages"])
        self.assertEqual((m["init_tools"], m["init_skills"], m["init_slash_commands"], m["init_agents"]), (1, 2, 3, 3))

    def test_a_request_without_tools_or_init_is_zeros_not_an_error(self):
        m = co.measure({"messages": []}, {})
        self.assertEqual((m["tools"], m["init_skills"]), (len("[]"), 0))

    def test_the_init_event_is_found_among_other_lines(self):
        lines = ["not json", json.dumps({"type": "system", "subtype": "hook"}), json.dumps(INIT)]
        self.assertEqual(co.init_event(lines)["skills"], ["a", "b"])
        self.assertEqual(co.init_event([]), {})


class FirstCallTest(unittest.TestCase):
    def test_the_first_top_level_non_synthetic_call_input_plus_cache(self):
        lines = [json.dumps(INIT),
                 assistant("s", {"input_tokens": 0, "output_tokens": 0}, model="<synthetic>"),
                 assistant("sub", {"input_tokens": 900}, parent="toolu_1"),
                 assistant("m1", {"input_tokens": 3, "cache_creation_input_tokens": 5120,
                                  "cache_read_input_tokens": 1840, "output_tokens": 8}),
                 assistant("m2", {"input_tokens": 2, "cache_read_input_tokens": 7000})]
        self.assertEqual(co.first_call(lines), 6963)

    def test_none_when_no_call_was_made(self):
        self.assertIsNone(co.first_call([json.dumps(INIT), json.dumps({"type": "result"})]))


class DefaultsTest(unittest.TestCase):
    def test_reads_the_claude_code_env_block_of_reference_conf(self):
        with open(os.path.join(REPO, "modules/config/src/main/resources/reference.conf"), encoding="utf-8") as f:
            d = co.defaults(f.read())
        names = [k for k, _ in d]
        self.assertIn("CLAUDE_CODE_DISABLE_ARTIFACT", names)
        self.assertTrue(all(v == "1" for _, v in d), d)
        for never in ("CLAUDE_CODE_DISABLE_THINKING", "CLAUDE_CODE_DISABLE_STRUCTURED_OUTPUTS",
                      "CLAUDE_CODE_DISABLE_WEB_FETCH"):
            self.assertNotIn(never, names)
        self.assertEqual(len(names), len(set(names)), "the ${?VAR} lines are not entries")

    def test_only_the_claude_code_block(self):
        text = """
          codex {
            env { NOT_THIS = "1" }
          }
          claude-code {
            command = "claude"
            env {
              A_VAR = "1"            # why
              A_VAR = ${?A_VAR}
              B_VAR = ""
            }
          }
        """
        self.assertEqual(co.defaults(text), [("A_VAR", "1"), ("B_VAR", "")])


class TableTest(unittest.TestCase):
    def test_deltas_against_the_first_arm(self):
        base = {"system": 100, "tools": 10, "messages": 20, "total": 130, "init_tools": 1, "init_skills": 37,
                "init_slash_commands": 70, "init_agents": 17}
        tuned = dict(base, total=110, init_skills=0)
        out = co.table([("none", base), ("tuned", tuned)]).splitlines()
        self.assertTrue(out[2].startswith("none") and "+0" in out[2], out[2])
        self.assertIn("-20", out[3])


class ServeTest(unittest.TestCase):
    def test_records_the_body_and_answers_a_stream_with_end_turn(self):
        out = tempfile.mkdtemp()
        srv = co.http.server.ThreadingHTTPServer(("127.0.0.1", 0), co.handler(out))
        threading.Thread(target=srv.serve_forever, daemon=True).start()
        try:
            conn = http.client.HTTPConnection("127.0.0.1", srv.server_address[1], timeout=5)
            body = json.dumps({"model": "m", "stream": True, "system": "s", "messages": []})
            conn.request("POST", "/v1/messages?beta=true", body, {"x-api-key": "dummy"})
            resp = conn.getresponse().read().decode()
            self.assertIn("event: message_start", resp)
            self.assertIn('"stop_reason": "end_turn"', resp)
            with open(os.path.join(out, "request-001.json"), encoding="utf-8") as f:
                self.assertEqual(json.load(f)["system"], "s")
            conn.request("POST", "/v1/messages/count_tokens", "{}")
            self.assertEqual(conn.getresponse().status, 404)
            with open(os.path.join(out, "paths.log"), encoding="utf-8") as f:
                log = f.read()
            self.assertNotIn("dummy", log, "headers are never recorded")
        finally:
            srv.shutdown()

    def test_with_a_tool_list_it_answers_mcp_initialize_and_tools_list_and_nothing_else(self):
        out = tempfile.mkdtemp()
        tools = [{"name": "get_source", "description": "d", "inputSchema": {"type": "object"}}]
        srv = co.http.server.ThreadingHTTPServer(("127.0.0.1", 0), co.handler(out, tools))
        threading.Thread(target=srv.serve_forever, daemon=True).start()
        try:
            conn = http.client.HTTPConnection("127.0.0.1", srv.server_address[1], timeout=5)

            def rpc(msg):
                conn.request("POST", "/mcp", json.dumps(msg), {"content-type": "application/json"})
                r = conn.getresponse()
                body = r.read().decode()
                return r.status, (json.loads(body) if body else None)

            status, init = rpc({"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {"protocolVersion": "2025-03-26"}})
            self.assertEqual((status, init["result"]["protocolVersion"]), (200, "2025-03-26"))
            self.assertIn("tools", init["result"]["capabilities"])
            self.assertEqual(rpc({"jsonrpc": "2.0", "method": "notifications/initialized"}), (202, None))
            _, listed = rpc({"jsonrpc": "2.0", "id": 2, "method": "tools/list"})
            self.assertEqual(listed["result"]["tools"], tools)
            _, call = rpc({"jsonrpc": "2.0", "id": 3, "method": "tools/call", "params": {"name": "get_source"}})
            self.assertEqual(call["error"]["code"], -32601, "a tool is never run")
            self.assertFalse(any(n.startswith("request-") for n in os.listdir(out)), "MCP traffic is not a model request")
        finally:
            srv.shutdown()

    def test_mcp_tool_characters_are_counted_apart(self):
        req = {"tools": [{"name": "mcp__histmersion__query", "description": "q"}, {"name": "StructuredOutput"}]}
        m = co.measure(req, {})
        self.assertEqual(m["mcp_tools"], len(json.dumps([req["tools"][0]])))


if __name__ == "__main__":
    unittest.main()
