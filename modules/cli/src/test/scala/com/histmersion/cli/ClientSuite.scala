package com.histmersion
package cli
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import java.io.InputStream
import java.net.{InetAddress, ServerSocket}
import java.nio.charset.StandardCharsets.US_ASCII
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
class ClientSuite extends munit.FunSuite:

  override val munitTimeout = scala.concurrent.duration.Duration(60, "s")

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

  private def hangUpAfterReading(): (ServerSocket, Client.ServerUrl) =
    val socket = new ServerSocket(0, 4, InetAddress.getLoopbackAddress)
    @annotation.tailrec
    def serve(): Unit =
      val served = scala.util.Try {
        val s = socket.accept()
        val in = s.getInputStream
        val head = readHead(in)
        val length = head.linesIterator
          .collectFirst { case l if l.toLowerCase.startsWith("content-length:") => l.drop(15).trim.toInt }
          .getOrElse(0)
        val _ = in.readNBytes(length)
        s.close()
      }
      if served.isSuccess then serve()
    val t = new Thread(() => serve())
    t.setDaemon(true)
    t.start()
    (socket, Loopback.url(socket.getLocalPort))

  private def readHead(in: InputStream): String =
    val out = new java.io.ByteArrayOutputStream
    @annotation.tailrec
    def loop(last: List[Int]): Unit =
      val b = in.read()
      if b >= 0 then
        out.write(b)
        val seen = (b :: last).take(4)
        if seen != List('\n', '\r', '\n', '\r').map(_.toInt) then loop(seen)
    loop(Nil)
    new String(out.toByteArray, US_ASCII)

  test("a write whose connection ends after it was sent: outcome unknown, with the request named"):
    val (socket, url) = hangUpAfterReading()
    try
      val r = new Client(url).backup
      assertEquals(r.left.map(_.exitCode), Left(ExitCode.OutcomeUnknown))
      val said = r.left.toOption.map(_.problems.toString).getOrElse("")
      assert(said.contains("/api/backup"), said)
    finally socket.close()

  test("a read whose connection ends: 'cannot reach', nothing changed"):
    val (socket, url) = hangUpAfterReading()
    try assertEquals(new Client(url).health.left.map(_.exitCode), Left(ExitCode.Unreachable))
    finally socket.close()

  test("a server that is not there: 'unreachable', for a write too"):
    val socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress)
    val url = Loopback.url(socket.getLocalPort)
    socket.close()
    assertEquals(new Client(url).backup.left.map(_.exitCode), Left(ExitCode.Unreachable))
