package com.histmersion.cli

import com.histmersion.core.{CalibreCliConfig, CalibreGap, CalibrePushRequest}
import com.sun.net.httpserver.{HttpExchange, HttpServer}
import io.circe.syntax.*

import java.net.InetSocketAddress
import java.nio.file.{Files, Path}

/** `calibre push`/`sync` take the original the SERVER stored when no file is given and no host-side
  * `originals-dir` holds one: it is downloaded over HTTP into a temporary directory, handed to
  * `calibredb add`, and removed. A stand-in server and a recording shell — no Calibre, no database. */
class CalibrePushSuite extends munit.FunSuite:

  private val Pdf = "%PDF-1.4 server copy"

  private def gap(ck: String, stored: Option[Boolean] = None) =
    CalibreGap(ck, "Bronze Age tin", List("Stocker, Sharon R."), Some("10.1/x"), Some(2019), None, None, Nil,
      Some("unpaywall"), storedOriginal = stored)

  /** The server: the push metadata of any citekey, the stored original of `zz_ck` only (404 with the
    * server's own message for any other), and the push receipt — every request recorded. */
  private final class Stub:
    val requests = java.util.concurrent.ConcurrentLinkedQueue[String]()
    val pushed   = java.util.concurrent.ConcurrentLinkedQueue[String]()
    private val srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    srv.createContext("/", (ex: HttpExchange) =>
      val path = ex.getRequestURI.getPath
      val q    = Option(ex.getRequestURI.getQuery).getOrElse("")
      requests.add(s"${ex.getRequestMethod} $path")
      val (code, body, headers) =
        if path == "/api/acquire/calibre/meta" then
          (200, gap(q.stripPrefix("citekey=")).asJson.noSpaces, List("Content-Type" -> "application/json"))
        else if path == "/api/sources/zz_ck/original" then
          (200, Pdf, List("Content-Type" -> "application/octet-stream",
            "Content-Disposition" -> "attachment; filename=\"zz_ck.pdf\""))
        else if path.endsWith("/original") then
          (404, """{"message":"no original stored for 'zz_none' — pass the file yourself (--file)","code":"not_found"}""",
            List("Content-Type" -> "application/json"))
        else if path == "/api/acquire/calibre/pushed" then
          val req = io.circe.parser.decode[CalibrePushRequest](String(ex.getRequestBody.readAllBytes())).toOption.get
          pushed.add(s"${req.citekey}=${req.calibreId}")
          (200, s"""{"citekey":"${req.citekey}","calibreId":"${req.calibreId}","ok":true}""",
            List("Content-Type" -> "application/json"))
        else (404, "", Nil)
      headers.foreach((k, v) => ex.getResponseHeaders.add(k, v))
      val bytes = body.getBytes("UTF-8")
      ex.sendResponseHeaders(code, if bytes.isEmpty then -1 else bytes.length.toLong)
      if bytes.nonEmpty then ex.getResponseBody.write(bytes)
      ex.close())
    srv.start()
    val url = s"http://127.0.0.1:${srv.getAddress.getPort}"
    def stop(): Unit = srv.stop(0)

  /** `calibredb`: records each command; `add` reads the file it was given (it must exist THEN). */
  private final class Recording extends Shell:
    @volatile var commands: List[List[String]] = Nil // a test double: the simplest thing
    @volatile var added: List[(Path, String)] = Nil
    def run(cmd: List[String]): Either[String, String] =
      commands = commands :+ cmd
      if cmd.lift(1).contains("add") then
        val f = cmd.drop(2).map(a => scala.util.Try(Path.of(a)).toOption).flatten.find(Files.isRegularFile(_))
          .getOrElse(throw AssertionError(s"calibredb add without an existing file: $cmd"))
        added = added :+ (f -> Files.readString(f))
        Right("Added book ids: 4242\n")
      else Right("")
    def isRunning(processMatch: String): Boolean = false

  private def calibre(shell: Shell) = Calibre(CalibreCliConfig("/nonexistent/library", "calibredb", "sqlite3", ""), shell)

  private def withStub[A](body: Stub => A): A =
    val s = Stub()
    try body(s) finally s.stop()

  test("no --file and no originals-dir: the server's stored original is downloaded, added, and removed"):
    withStub { s =>
      val shell = Recording()
      val got = CalibreCmd.pushOne(Client(s.url), "zz_ck", None, calibre(shell), originals = None)
      assertEquals(got.map(_._1), Right(4242))
      val (file, content) = shell.added.head
      assertEquals(content, Pdf, "calibredb add got the server's bytes")
      assertEquals(file.getFileName.toString, "zz_ck.pdf", "named <citekey>.<ext> — the extension tells Calibre the format")
      assert(!Files.exists(file) && !Files.exists(file.getParent), "the temporary copy is gone after the push")
      assertEquals(s.pushed.toArray.toList, List("zz_ck=4242"), "the new id is recorded back")
    }

  test("a host-side original under originals-dir wins: the server is not asked for the file"):
    withStub { s =>
      val dir = Files.createTempDirectory("zz-originals-")
      Files.createDirectories(dir.resolve("zz_ck"))
      Files.writeString(dir.resolve("zz_ck/paper.pdf"), "%PDF local")
      val shell = Recording()
      val got = CalibreCmd.pushOne(Client(s.url), "zz_ck", None, calibre(shell), originals = Some(dir.toString))
      assertEquals(got.map(_._1), Right(4242))
      assertEquals(shell.added.map(_._2), List("%PDF local"))
      assert(!s.requests.toArray.exists(_.toString.endsWith("/original")), s.requests)
    }

  test("--file wins over both"):
    withStub { s =>
      val f = Files.createTempFile("zz-given-", ".pdf"); Files.writeString(f, "%PDF given")
      val shell = Recording()
      assertEquals(CalibreCmd.pushOne(Client(s.url), "zz_ck", Some(f), calibre(shell), originals = None).map(_._1), Right(4242))
      assertEquals(shell.added.map(_._2), List("%PDF given"))
      assert(!s.requests.toArray.exists(_.toString.endsWith("/original")))
    }

  test("no stored original and none on this host: nothing is added, and the error says what to do"):
    withStub { s =>
      val shell = Recording()
      val got = CalibreCmd.pushOne(Client(s.url), "zz_none", None, calibre(shell), originals = None)
      val msg = got.left.map(_.message).swap.getOrElse(fail(s"pushed: $got"))
      assert(msg.contains("no original stored") && msg.contains("--file") && msg.contains("originals-dir"), msg)
      assertEquals(shell.commands, Nil, "calibredb never ran")
      assert(s.pushed.isEmpty)
    }

  test("sync: a gap is pushable with a host file or a stored original (or when the server does not say)"):
    val local = Map("zz_local" -> Path.of("/x/zz_local/paper.pdf"))
    val gaps = List(gap("zz_stored", Some(true)), gap("zz_missing", Some(false)), gap("zz_local", Some(false)),
      gap("zz_unknown", None))
    val (ready, skipped) = Calibre.pushable(gaps, local.get)
    assertEquals(ready.map(_.citekey), List("zz_stored", "zz_local", "zz_unknown"))
    assertEquals(skipped.map(_.citekey), List("zz_missing"))

  test("where a push takes its file from: --file, else the host original, else the server"):
    val (a, b) = (Path.of("/a.pdf"), Path.of("/b.pdf"))
    assertEquals(Calibre.pushFile(Some(a), Some(b)), Calibre.PushFile.Given(a))
    assertEquals(Calibre.pushFile(None, Some(b)), Calibre.PushFile.Local(b))
    assertEquals(Calibre.pushFile(None, None), Calibre.PushFile.Server)
