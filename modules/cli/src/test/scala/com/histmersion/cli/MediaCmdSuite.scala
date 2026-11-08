package com.histmersion
package cli

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.monovore.decline.Command
import com.sun.net.httpserver.{HttpExchange, HttpServer}
import io.circe.syntax.*
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.media.ImportJobKinds

/** #1491: `histmersion media import wikidata` starts the server's import JOB — the request answers at once — and
  * follows it with the `n/total` progress line the job reports, to its summary; `--no-wait` prints the id and returns;
  * a job that fails exits 2. A stand-in server, no database.
  */
class MediaCmdSuite extends munit.FunSuite:

  private given console: com.histmersion.util.console.Console = com.histmersion.util.console.Console.standard

  private val running = JobInfo(
    SpatialHierarchyResult.Job.Id("j7"),
    JobKinds.Wire("media-import"),
    JobInfo.Status("running"),
    progress = Some(JobInfo.Progress("100/300 place(s)/event(s) asked · batch 2")),
    detail = Some(
      ImportJobKinds
        .Media(Some(JobKinds.Subject("Wikidata pictures of up to 300 places/events not asked yet")))
    )
  )
  private val done =
    running.modify(_.status).setTo(JobInfo.Status("done")).modify(_.progress).setTo(Some(JobInfo.Progress("300 place(s)/event(s) asked in 6 batch(es) · 284 stored")))

  /** Answers the job start with `queued`, then each poll of the job with the next of `polls`. */
  private def withServer[A](polls: List[JobInfo])(body: (Client.ServerUrl, ConcurrentLinkedQueue[String]) => A): A =
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
            (202, running.modify(_.status).setTo(JobInfo.Status("queued")).modify(_.progress).setTo(None).asJson.noSpaces)
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
    try body(Client.ServerUrl(s"http://127.0.0.1:${srv.getAddress.getPort}"), seen)
    finally srv.stop(0)

  private val cmd = Command("histmersion", "test")(MediaCmd.command)

  /** Run the command line against `url`; its result, stdout and stderr. */
  private def run(url: Client.ServerUrl, args: String*): (Either[Cli.Error, Unit], String, String) =
    val f = cmd.parse(args.toList, Map.empty).fold(h => fail(h.toString), identity)
    val (out, err) = (ByteArrayOutputStream(), ByteArrayOutputStream())
    val r = Console.withOut(out)(Console.withErr(err)(f(new Client(url))))
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
    withServer(List(running.modify(_.status).setTo(JobInfo.Status("failed")).modify(_.error).setTo(Some(JobInfo.Error("interrupted"))))) { (url, _) =>
      val (r, _, _) = run(url, "media", "import", "wikidata")
      assertEquals(r.left.map(_.exitCode), Left(ExitCode.Refused))
      assert(r.left.exists(_.message.value.contains("interrupted")), r.toString)
    }
