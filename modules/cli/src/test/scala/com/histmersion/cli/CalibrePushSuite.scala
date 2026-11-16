package com.histmersion
package cli
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.CalibreCliConfig
import com.histmersion.api.Api
import com.histmersion.api.library.{CalibreGap, CalibreGaps, CalibrePushRequest, Citekey, Source}
import com.sun.net.httpserver.{HttpExchange, HttpServer}

import java.net.InetSocketAddress
import java.nio.file.{Files, Path}
import com.histmersion.api.library.Doi
import com.histmersion.api.catalogue.Year

/** `calibre push`/`sync` take the original the SERVER stored when no file is given and no host-side `originals-dir`
  * holds one: it is downloaded over HTTP into a temporary directory, handed to `calibredb add`, and removed. A stand-in
  * server and a recording shell — no Calibre, no database.
  */
class CalibrePushSuite extends munit.FunSuite:

  private given console: com.histmersion.util.console.Console = com.histmersion.util.console.Console.standard

  private val Pdf = "%PDF-1.4 server copy"

  private val Stored = Some(CalibreGap.StoredOriginal(true))
  private val NotStored = Some(CalibreGap.StoredOriginal(false))

  private def gap(
      ck: Citekey,
      stored: Option[CalibreGap.StoredOriginal] = None,
      provenance: CalibreGap.Provenance = CalibreGap.Provenance("unpaywall")
  ) =
    CalibreGap(
      ck,
      CalibreGap.Title("Bronze Age tin"),
      List(CalibreGap.Authors("Stocker, Sharon R.")),
      Some(Doi("10.1/x")),
      Some(Year(2019)),
      None,
      None,
      Nil,
      Some(provenance),
      storedOriginal = stored
    )

  /** The server: the push metadata of any citekey, the stored original of `zz_ck` only (404 with the server's own
    * message for any other), and the push receipt — every request recorded.
    */
  final private class Stub(listed: List[CalibreGap] = Nil):
    val requests = java.util.concurrent.ConcurrentLinkedQueue[String]()
    val pushed = java.util.concurrent.ConcurrentLinkedQueue[String]()
    private val srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    srv.createContext(
      "/",
      (ex: HttpExchange) =>
        val path = ex.getRequestURI.getPath
        val q = Option(ex.getRequestURI.getQuery).getOrElse("")
        requests.add(s"${ex.getRequestMethod} $path")
        val (code, body, headers) =
          if path == "/api/acquire/calibre/gaps" then
            (
              200,
              CalibreGaps(CalibreGaps.Count(listed.size), listed).asJson.noSpaces,
              List("Content-Type" -> "application/json")
            )
          else if path == "/api/acquire/calibre/meta" then
            (200, gap(Citekey(q.stripPrefix("citekey="))).asJson.noSpaces, List("Content-Type" -> "application/json"))
          else if path == "/api/sources/zz_ck/original" then
            (
              200,
              Pdf,
              List(
                "Content-Type" -> "application/octet-stream",
                "Content-Disposition" -> "attachment; filename=\"zz_ck.pdf\""
              )
            )
          else if path.endsWith("/original") then
            (
              404,
              """{"problems":["no original stored for 'zz_none' — pass the file yourself (--file)"],"code":"not_found"}""",
              List("Content-Type" -> "application/json")
            )
          else if path == "/api/acquire/calibre/pushed" then
            val req = RawJson.decode[CalibrePushRequest](String(ex.getRequestBody.readAllBytes())).toOption.get
            pushed.add(s"${req.citekey}=${req.calibreId}")
            (
              200,
              s"""{"citekey":"${req.citekey}","calibreId":"${req.calibreId}","ok":true}""",
              List("Content-Type" -> "application/json")
            )
          else (404, "", Nil)
        headers.foreach((k, v) => ex.getResponseHeaders.add(k, v))
        val bytes = body.getBytes("UTF-8")
        ex.sendResponseHeaders(code, if bytes.isEmpty then -1 else bytes.length.toLong)
        if bytes.nonEmpty then ex.getResponseBody.write(bytes)
        ex.close()
    )
    srv.start()
    val url = Client.ServerUrl(s"http://127.0.0.1:${srv.getAddress.getPort}")
    def stop(): Unit = srv.stop(0)

  /** `calibredb`: records each command; `add` reads the file it was given (it must exist THEN). */
  final private class Recording extends Shell:
    @volatile var commands: List[List[String]] = Nil // a test double: the simplest thing
    @volatile var added: List[(Path, String)] = Nil
    def run(cmd: List[String]): Either[Problem, String] =
      commands = commands :+ cmd
      if cmd.lift(1).contains("add") then
        val f = cmd
          .drop(2)
          .map(a => scala.util.Try(Path.of(a)).toOption)
          .flatten
          .find(Files.isRegularFile(_))
          .getOrElse(throw AssertionError(s"calibredb add without an existing file: $cmd"))
        added = added :+ (f -> Files.readString(f))
        Right("Added book ids: 4242\n")
      else Right("")
    def isRunning(processMatch: Shell.ProcessMatch): Boolean = false

  private def calibre(shell: Shell) =
    Calibre(
      CalibreCliConfig(
        CalibreCliConfig.LibraryDir("/nonexistent/library"),
        CalibreCliConfig.Calibredb("calibredb"),
        CalibreCliConfig.Sqlite("sqlite3"),
        CalibreCliConfig.OriginalsDir("")
      ),
      shell
    )

  private def withStub[A](body: Stub => A): A = withGaps(Nil)(body)

  private def withGaps[A](listed: List[CalibreGap])(body: Stub => A): A =
    val s = Stub(listed)
    try body(s)
    finally s.stop()

  test("no --file and no originals-dir: the server's stored original is downloaded, added, and removed"):
    withStub { s =>
      val shell = Recording()
      val got = CalibreCmd.pushOne(new Client(s.url), Citekey("zz_ck"), None, calibre(shell), originals = None)
      assertEquals(got.map(_._1), Right(Calibre.Book.Id(4242)))
      val (file, content) = shell.added.head
      assertEquals(content, Pdf, "calibredb add got the server's bytes")
      assertEquals(
        file.getFileName.toString,
        "zz_ck.pdf",
        "named <citekey>.<ext> — the extension tells Calibre the format"
      )
      assert(!Files.exists(file) && !Files.exists(file.getParent), "the temporary copy is gone after the push")
      assertEquals(s.pushed.toArray.toList, List("zz_ck=4242"), "the new id is recorded back")
    }

  test("a host-side original under originals-dir wins: the server is not asked for the file"):
    withStub { s =>
      val dir = Files.createTempDirectory("zz-originals-")
      Files.createDirectories(dir.resolve("zz_ck"))
      Files.writeString(dir.resolve("zz_ck/paper.pdf"), "%PDF local")
      val shell = Recording()
      val got = CalibreCmd.pushOne(
        new Client(s.url),
        Citekey("zz_ck"),
        None,
        calibre(shell),
        originals = Some(CalibreCliConfig.OriginalsDir(dir.toString))
      )
      assertEquals(got.map(_._1), Right(Calibre.Book.Id(4242)))
      assertEquals(shell.added.map(_._2), List("%PDF local"))
      assert(!s.requests.toArray.exists(_.toString.endsWith("/original")), s.requests)
    }

  test("--file wins over both"):
    withStub { s =>
      val f = Files.createTempFile("zz-given-", ".pdf"); Files.writeString(f, "%PDF given")
      val shell = Recording()
      assertEquals(
        CalibreCmd.pushOne(new Client(s.url), Citekey("zz_ck"), Some(f), calibre(shell), originals = None).map(_._1),
        Right(Calibre.Book.Id(4242))
      )
      assertEquals(shell.added.map(_._2), List("%PDF given"))
      assert(!s.requests.toArray.exists(_.toString.endsWith("/original")))
    }

  test("no stored original and none on this host: nothing is added, and the error says what to do"):
    withStub { s =>
      val shell = Recording()
      val got = CalibreCmd.pushOne(new Client(s.url), Citekey("zz_none"), None, calibre(shell), originals = None)
      val msg = got.left.map(_.message.sentence).swap.getOrElse(fail(s"pushed: $got"))
      assert(msg.contains("no original stored") && msg.contains("--file") && msg.contains("originals-dir"), msg)
      assertEquals(shell.commands, Nil, "calibredb never ran")
      assert(s.pushed.isEmpty)
    }

  test("classify: a stored original or a host file counts; no file anywhere, or already in Calibre, does not"):
    val local = Map(Citekey("zz_local") -> Path.of("/x/zz_local/paper.pdf"))
    val gaps = List(
      gap(Citekey("zz_stored"), Stored),
      gap(Citekey("zz_missing"), NotStored),
      gap(Citekey("zz_local"), NotStored),
      gap(Citekey("zz_unknown"), None),
      gap(Citekey("zz_cal"), Stored, provenance = CalibreGap.Provenance("Calibre")),
      gap(Citekey("zz_linked"), Stored).modify(_.calibreId).setTo(Some(Source.CalibreId("12")))
    )
    val got = Calibre.classify(gaps, local.get).map(e => e.gap.citekey -> (e.original.label, e.counted))
    def label(s: String) = Calibre.GapOriginal.Label(s)
    def counted(b: Boolean) = Calibre.GapEntry.Counted(b)
    assertEquals(
      got,
      List(
        Citekey("zz_stored") -> (label("stored"), counted(true)),
        Citekey("zz_missing") -> (label("none"), counted(false)),
        Citekey("zz_local") -> (label("local"), counted(true)),
        Citekey("zz_unknown") -> (label("unknown"), counted(true)),
        Citekey("zz_cal") -> (label("stored"), counted(false)),
        Citekey("zz_linked") -> (label("stored"), counted(false))
      )
    )

  test("the report: counted gaps in the table, the no-file records as ONE line, never rows"):
    val es = Calibre.classify(
      List(gap(Citekey("zz_stored"), Stored), gap(Citekey("zz_none1"), NotStored), gap(Citekey("zz_none2"), NotStored)),
      _ => None
    )
    val md = Calibre.markdownGaps(es)
    assert(
      md.contains("### 1 source(s) not in Calibre") && md.contains("| `zz_stored` |") && md.contains("| stored |"),
      md
    )
    assert(!md.contains("zz_none1") && !md.contains("zz_none2"), md)
    assert(md.contains("2 records without a fetched file — not counted; see the acquire list"), md)
    val onlyNone = Calibre.markdownGaps(es.filterNot(_.counted.value))
    assert(onlyNone.startsWith("_Every source with a fetched file is in Calibre._"), onlyNone)
    assertEquals(Calibre.markdownGaps(Nil), "_Every source with a fetched file is in Calibre._")

  private def captured(f: => Either[Cli.Error, Unit]): (Either[Cli.Error, Unit], String) =
    val out = java.io.ByteArrayOutputStream()
    val r = Console.withOut(out)(f)
    (r, out.toString("UTF-8"))

  test("gaps --quiet over the server: a stored original and a host file count; no file does not"):
    val dir = Files.createTempDirectory("zz-originals-")
    Files.createDirectories(dir.resolve("zz_local"))
    Files.writeString(dir.resolve("zz_local/paper.pdf"), "%PDF local")
    withGaps(
      List(gap(Citekey("zz_stored"), Stored), gap(Citekey("zz_local"), NotStored), gap(Citekey("zz_none"), NotStored))
    ) { s =>
      val (r, out) = captured(
        CalibreCmd.listGaps(
          new Client(s.url),
          quiet = Cli.QuietOutput(true),
          None,
          json = Cli.JsonOutput(false),
          Some(CalibreCliConfig.OriginalsDir(dir.toString))
        )
      )
      val err = r.swap.getOrElse(fail(s"no reminder: $r"))
      assertEquals(err.exitCode, ExitCode.Usage)
      assert(err.message.sentence.startsWith("2 source(s) are not in Calibre yet (zz_stored, zz_local)"), err.message)
      assert(!err.message.sentence.contains("zz_none"), err.message)
      assertEquals(out, "")
    }

  test("gaps --quiet: only no-file records left (and Calibre's own) — silent, exit 0"):
    withGaps(
      List(
        gap(Citekey("zz_none"), NotStored),
        gap(Citekey("zz_stub"), NotStored),
        gap(Citekey("zz_cal"), Stored, provenance = CalibreGap.Provenance("calibre"))
      )
    ) { s =>
      assertEquals(
        captured(
          CalibreCmd
            .listGaps(new Client(s.url), quiet = Cli.QuietOutput(true), None, json = Cli.JsonOutput(false), None)
        ),
        (Right(()), "")
      )
      val (full, report) =
        captured(
          CalibreCmd.listGaps(
            new Client(s.url),
            quiet = Cli.QuietOutput(false),
            None,
            json = Cli.JsonOutput(false),
            None
          )
        )
      assertEquals(full, Right(()))
      assert(
        report.contains("_Every source with a fetched file is in Calibre._") &&
          report.contains("2 records without a fetched file — not counted"),
        report
      )
    }

  test("gaps --json keeps every row, with original and counted"):
    withGaps(List(gap(Citekey("zz_stored"), Stored), gap(Citekey("zz_none"), NotStored))) { s =>
      val (r, out) =
        captured(
          CalibreCmd.listGaps(
            new Client(s.url),
            quiet = Cli.QuietOutput(false),
            None,
            json = Cli.JsonOutput(true),
            None
          )
        )
      assertEquals(r, Right(()))
      val j = RawJson.parse(out).fold(e => fail(e.toString), identity).cursor
      assertEquals(j.get[Int]("count"), Right(1))
      assertEquals(j.get[Int]("uncounted"), Right(1))
      assertEquals(j.get[Int]("total"), Right(2))
      val rows = j.downField("gaps").as[List[Json]].getOrElse(Nil).map(_.cursor)
      assertEquals(
        rows.map(r => (r.get[String]("citekey"), r.get[String]("original"), r.get[Boolean]("counted"))),
        List((Right("zz_stored"), Right("stored"), Right(true)), (Right("zz_none"), Right("none"), Right(false)))
      )
    }

  test("sync --apply pushes the counted gaps only; the no-file records are one summary note, not skipped errors"):
    withGaps(List(gap(Citekey("zz_ck"), Stored), gap(Citekey("zz_none"), NotStored))) { s =>
      val shell = Recording()
      val (r, out) =
        captured(
          CalibreCmd.syncGaps(
            new Client(s.url),
            apply = Api.Apply(true),
            None,
            json = Cli.JsonOutput(false),
            calibre(shell),
            None
          )
        )
      assertEquals(r, Right(()))
      assertEquals(s.pushed.toArray.toList, List("zz_ck=4242"))
      assert(!s.requests.toArray.exists(_.toString.contains("zz_none")), s.requests)
      assert(out.contains("pushed 1 source(s), 0 failed · 1 record without a fetched file — not counted"), out)
      assert(!out.contains("skipped"), out)
      val (dry, plan) =
        captured(
          CalibreCmd.syncGaps(
            new Client(s.url),
            apply = Api.Apply(false),
            None,
            json = Cli.JsonOutput(false),
            calibre(shell),
            None
          )
        )
      assertEquals(dry, Right(()))
      assert(plan.contains("Dry run: 1 source(s) would be pushed · 1 record without a fetched file"), plan)
    }

  test("where a push takes its file from: --file, else the host original, else the server"):
    val (a, b) = (Path.of("/a.pdf"), Path.of("/b.pdf"))
    assertEquals(Calibre.pushFile(Some(a), Some(b)), Calibre.PushFile.Given(a))
    assertEquals(Calibre.pushFile(None, Some(b)), Calibre.PushFile.Local(b))
    assertEquals(Calibre.pushFile(None, None), Calibre.PushFile.Server)
