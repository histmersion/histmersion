package com.histmersion
package cli
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

/** What the CLI says when the server answers outside an endpoint's contract. */
class ClientSuite extends munit.FunSuite:

  // a refused write used to read "cannot reach … Mismatch(404,401) — is the server running?"
  test("a 401 names the missing API key and where the CLI reads it, never 'cannot reach' nor a command-line flag"):
    val m =
      Client
        .unexpected(Client.HttpStatus(401), Client.ServerUrl("http://h:8000"), write = Client.Write(true))
        .sentence
    assert(m.contains("API key") && m.contains("HISTMERSION_TOKEN") && m.contains("cli.token-file"), m)
    assert(!m.contains("cannot reach") && !m.contains("--token"), m)

  test("a 403 is a refusal the key does not lift: it says so without asking for the key"):
    val m =
      Client
        .unexpected(Client.HttpStatus(403), Client.ServerUrl("http://h:8000"), write = Client.Write(true))
        .sentence
    assertEquals(m, "http://h:8000 refused this write (HTTP 403)")

  test("the token comes from HISTMERSION_TOKEN, else from the token file's first line, never from both"):
    val noFile: Either[String, Option[Client.Token]] = Left("the file must not be read")
    assertEquals(Client.Token.chosen(Some(" k3y "), noFile), Right(Some(Client.Token("k3y"))))
    assertEquals(Client.Token.chosen(Some("  "), Right(Some(Client.Token("f")))), Right(Some(Client.Token("f"))))
    assertEquals(Client.Token.chosen(None, Right(None)), Right(None))
    assertEquals(Client.Token.chosen(None, Left("unreadable")), Left("unreadable"))
    assertEquals(Client.Token.firstLine("\n  s3cret  \nsecond\n"), Some(Client.Token("s3cret")))
    assertEquals(Client.Token.firstLine(" \n\n"), None)

  test("other unexpected answers report the status as it is"):
    assert(
      Client
        .unexpected(Client.HttpStatus(404), Client.ServerUrl("http://h:8000"), write = Client.Write(false))
        .sentence
        .contains("no such endpoint")
    )
    assertEquals(
      Client.unexpected(Client.HttpStatus(502), Client.ServerUrl("http://h:8000"), write = Client.Write(false)),
      Problem.Unclassified(Legacy.Text("unexpected answer from http://h:8000: HTTP 502"))
    )

  // #819: a refused GET (a backup or original download) said "refused this write"
  test("a refused read says 'this request', a refused write says 'this write'"):
    assert(
      Client
        .unexpected(Client.HttpStatus(401), Client.ServerUrl("http://h:8000"), write = Client.Write(false))
        .sentence
        .contains("refused this request")
    )
    assert(
      !Client
        .unexpected(Client.HttpStatus(401), Client.ServerUrl("http://h:8000"), write = Client.Write(false))
        .sentence
        .contains("write")
    )
    assert(
      Client
        .unexpected(Client.HttpStatus(401), Client.ServerUrl("http://h:8000"), write = Client.Write(true))
        .sentence
        .contains("refused this write")
    )

  test("only POST, PUT, PATCH and DELETE count as writes"):
    import sttp.model.Method
    assertEquals(
      List(Method.GET, Method.HEAD, Method.POST, Method.PUT, Method.PATCH, Method.DELETE).map(Client.isWrite),
      List(false, false, true, true, true, true)
    )
