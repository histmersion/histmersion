package com.histmersion.cli

/** What the CLI says when the server answers outside an endpoint's contract. */
class ClientSuite extends munit.FunSuite:

  // a refused write used to read "cannot reach … Mismatch(404,401) — is the server running?"
  test("a 401/403 names the missing API key and how to pass it, never 'cannot reach'"):
    for code <- List(401, 403) do
      val m = Client.unexpected(code, "http://h:8000")
      assert(m.contains("API key") && m.contains("--token") && m.contains("HISTMERSION_TOKEN"), m)
      assert(!m.contains("cannot reach"), m)

  test("other unexpected answers report the status as it is"):
    assert(Client.unexpected(404, "http://h:8000").contains("no such endpoint"))
    assertEquals(Client.unexpected(502, "http://h:8000"), "unexpected answer from http://h:8000: HTTP 502")
