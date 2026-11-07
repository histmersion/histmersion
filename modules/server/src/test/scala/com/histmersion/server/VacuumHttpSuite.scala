package com.histmersion.server

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import ox.supervised
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.maintenance.{Vacuum, VacuumPlan}

/** `POST /api/maintenance/vacuum` and the MCP `vacuum` tool (#1445) over real HTTP: the request answers at once with a
  * `vacuum` job, the job runs VACUUM (ANALYZE) outside any transaction (inside one, Postgres refuses it: "VACUUM cannot
  * run inside a transaction block") and ends `done` with a line per table. Gated on HISTMERSION_TEST_DB_URL like the
  * other server suites. Vacuums only small tables, so it is quick and leaves the other suites' fixtures alone.
  */
class VacuumHttpSuite extends munit.FunSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val client = java.net.http.HttpClient.newHttpClient()

  private def send(req: java.net.http.HttpRequest) =
    val r = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)

  private def get(url: String) = send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).build())

  private def post(url: String, body: String) =
    send(
      java.net.http.HttpRequest
        .newBuilder(java.net.URI.create(url))
        .header("Content-Type", "application/json")
        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
        .build()
    )

  private def job(body: String): JobInfo =
    io.circe.parser.decode[JobInfo](body).fold(e => fail(s"not a job: $body ($e)"), identity)

  /** Poll the job until it ends (a vacuum of two tiny tables takes well under a second). */
  private def await(url: String, id: String, left: Int = 200): JobInfo =
    val (s, b) = get(s"$url/api/jobs/$id")
    assertEquals(s, 200, b)
    val j = job(b)
    if j.isTerminal || left == 0 then j
    else
      Thread.sleep(50)
      await(url, id, left - 1)

  test("a vacuum is a job: VACUUM (ANALYZE) outside a transaction, a line per table; bad tables are refused"):
    val base = AppConfig.load()
    val cfg = base.modify(_.db).setTo(base.db.modify(_.url).setTo(testDb.get))
    supervised {
      val b = Composition.server(cfg, com.histmersion.config.ServerConfig.Host("127.0.0.1"), com.histmersion.config.ServerConfig.Port(0)).start()
      val url = s"http://127.0.0.1:${b.port}"

      val (s, body) =
        post(s"$url/api/maintenance/vacuum", """{"analyze":true,"tables":["place_overrides","place_removals"]}""")
      assertEquals(s, 200, body)
      val started = job(body)
      assertEquals(started.kind, Vacuum.JobKind)
      assertEquals(
        started.detail.flatMap(VacuumPlan.Detail.of),
        Some(VacuumPlan(com.histmersion.api.maintenance.VacuumPlan.Analyze(true), List(com.histmersion.api.maintenance.Vacuum.Request.Tables("place_overrides"), com.histmersion.api.maintenance.Vacuum.Request.Tables("place_removals"))))
      )
      val done = await(url, started.id)
      assertEquals((done.status, done.error), ("done", None), done.toString)
      val summary = done.progress.getOrElse("")
      assert(
        summary.startsWith("place_overrides: ") && summary.contains("; place_removals: ") && summary.contains(
          "dead rows"
        ),
        summary
      )

      assertEquals(post(s"$url/api/maintenance/vacuum", """{"tables":["no_such_table"]}""")._1, 400)
      assertEquals(post(s"$url/api/maintenance/vacuum", """{"tables":["places; DROP TABLE places"]}""")._1, 400)
      assertEquals(post(s"$url/api/maintenance/vacuum", """{"full":true}""")._1, 400, "unknown keys are refused")

      // the MCP tool reaches the same handler
      // (the first job's thread lets go of the one-at-a-time flag just after it records `done`)
      def mcp(left: Int = 20): String =
        val (_, r) = send(
          java.net.http.HttpRequest
            .newBuilder(java.net.URI.create(s"$url/mcp"))
            .header("content-type", "application/json")
            .header("accept", "application/json, text/event-stream")
            .POST(
              java.net.http.HttpRequest.BodyPublishers.ofString(
                """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"vacuum","arguments":{"tables":["place_removals"]}}}"""
              )
            )
            .build()
        )
        if r.contains("already running") && left > 0 then { Thread.sleep(50); mcp(left - 1) }
        else r
      val mb = mcp()
      assert(mb.contains(" · vacuum") && mb.contains("VACUUM of place_removals"), mb)
      val id = "[0-9a-f-]{36}".r.findFirstIn(mb).getOrElse(fail(mb))
      assertEquals(await(url, id).status, "done")

      b.stop()
    }
