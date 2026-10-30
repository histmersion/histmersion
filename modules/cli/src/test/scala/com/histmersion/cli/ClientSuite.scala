package com.histmersion.cli

/** What the CLI says when the server answers outside an endpoint's contract. */
class ClientSuite extends munit.FunSuite:

  // a refused write used to read "cannot reach … Mismatch(404,401) — is the server running?"
  test("a 401/403 names the missing API key and how to pass it, never 'cannot reach'"):
    for code <- List(401, 403) do
      val m = Client.unexpected(code, "http://h:8000", write = true)
      assert(m.contains("API key") && m.contains("--token") && m.contains("HISTMERSION_TOKEN"), m)
      assert(!m.contains("cannot reach"), m)

  test("other unexpected answers report the status as it is"):
    assert(Client.unexpected(404, "http://h:8000", write = false).contains("no such endpoint"))
    assertEquals(
      Client.unexpected(502, "http://h:8000", write = false),
      "unexpected answer from http://h:8000: HTTP 502"
    )

  // #819: a refused GET (a backup or original download) said "refused this write"
  test("a refused read says 'this request', a refused write says 'this write'"):
    assert(Client.unexpected(401, "http://h:8000", write = false).contains("refused this request"))
    assert(!Client.unexpected(401, "http://h:8000", write = false).contains("write"))
    assert(Client.unexpected(401, "http://h:8000", write = true).contains("refused this write"))

  test("only POST, PUT, PATCH and DELETE count as writes"):
    import sttp.model.Method
    assertEquals(
      List(Method.GET, Method.HEAD, Method.POST, Method.PUT, Method.PATCH, Method.DELETE).map(Client.isWrite),
      List(false, false, true, true, true, true)
    )
