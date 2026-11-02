package com.histmersion.cli

import com.histmersion.api.*
import com.histmersion.config.*
import com.monovore.decline.Command
import com.sun.net.httpserver.{HttpExchange, HttpServer}
import io.circe.syntax.*
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

/** #1491: `histmersion media import wikidata` starts the server's import JOB — the request answers at once — and
  * follows it with the `n/total` progress line the job reports, to its summary; `--no-wait` prints the id and returns;
  * a job that fails exits 2. A stand-in server, no database.
  */
class MediaCmdSuite extends munit.FunSuite:

  private val running = JobInfo(
    "j7",
    "media-import",
    "running",
    progress = Some("100/300 place(s)/event(s) asked · batch 2"),
    detail = Some(
      com.histmersion.api.media.imports.ImportJobKinds
        .Media(Some("Wikidata pictures of up to 300 places/events not asked yet"))
    )
  )
  private val done =
    running.copy(status = "done", progress = Some("300 place(s)/event(s) asked in 6 batch(es) · 284 stored"))

  /** Answers the job start with `queued`, then each poll of the job with the next of `polls`. */
  private def withServer[A](polls: List[JobInfo])(body: (String, ConcurrentLinkedQueue[String]) => A): A =
    val seen = ConcurrentLinkedQueue[String]()
    val next = polls.iterator
    // `var`: the answer repeated once the list runs out
    var last = polls.head
    val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    srv.createContext(
      "/",
      (ex: HttpExchange) =>
        val path = ex.getRequestURI.getPath
        val req = new String(ex.getRequestBody.readAllBytes(), UTF_8)
        seen.add(s"${ex.getRequestMethod} $path $req".trim)
        val (code, json) =
          if ex.getRequestMethod == "POST" && path == "/api/media/import/job" then
            (202, running.copy(status = "queued", progress = None).asJson.noSpaces)
          else if path == s"/api/jobs/${running.id}" then
            synchronized { if next.hasNext then last = next.next() }
            (200, last.asJson.noSpaces)
          else (404, """{"message":"no"}""")
        val bytes = json.getBytes(UTF_8)
        ex.getResponseHeaders.add("Content-Type", "application/json")
        ex.sendResponseHeaders(code, bytes.length.toLong)
        ex.getResponseBody.write(bytes)
        ex.close()
    )
    srv.start()
    try body(s"http://127.0.0.1:${srv.getAddress.getPort}", seen)
    finally srv.stop(0)

  private val cmd = Command("histmersion", "test")(MediaCmd.command)

  /** Run the command line against `url`; its result, stdout and stderr. */
  private def run(url: String, args: String*): (Either[CliError, Unit], String, String) =
    val f = cmd.parse(args.toList, Map.empty).fold(h => fail(h.toString), identity)
    val (out, err) = (ByteArrayOutputStream(), ByteArrayOutputStream())
    val r = Console.withOut(out)(Console.withErr(err)(f(Client(url))))
    (r, out.toString(UTF_8), err.toString(UTF_8))

  test("the import starts a job and follows its n/total progress to the summary"):
    withServer(List(running, done)) { (url, seen) =>
      val (r, out, err) = run(url, "media", "import", "wikidata", "--limit", "300")
      assertEquals(r, Right(()))
      assert(
        seen.asScala.exists(s =>
          s.startsWith("POST /api/media/import/job") && s.contains("\"limit\":300") &&
            s.contains("\"dryRun\":false")
        ),
        seen.toString
      )
      assert(out.contains("media import job `j7` started: Wikidata pictures of up to 300"), out)
      assert(err.contains("job j7: running — 100/300 place(s)/event(s) asked"), err)
      assert(out.contains("300 place(s)/event(s) asked in 6 batch(es) · 284 stored"), out)
    }

  test("--no-wait prints the job id and returns without polling"):
    withServer(List(running)) { (url, seen) =>
      val (r, out, _) = run(url, "media", "import", "wikidata", "--no-wait")
      assertEquals(r, Right(()))
      assert(out.contains("`j7` started"), out)
      assert(!seen.asScala.exists(_.contains("/api/jobs/")), seen.toString)
    }

  test("a job that fails exits 2 with its reason"):
    withServer(List(running.copy(status = "failed", error = Some("interrupted")))) { (url, _) =>
      val (r, _, _) = run(url, "media", "import", "wikidata")
      assertEquals(r.left.map(_.exitCode), Left(ExitCode.Refused))
      assert(r.left.exists(_.message.contains("interrupted")), r.toString)
    }
