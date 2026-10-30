package com.histmersion.server

import com.augustnagro.magnum.*
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import ox.supervised

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

/** The list filters over real HTTP and MCP (core `ListFilters`): `/api/agents/approvals`, `/api/agents/runs` and
  * `/api/jobs` filter on the server, the body stays the plain array, the facet counts come back in their header, a bad
  * value is a 400 naming the allowed words, and the MCP list tools take the same filters and print the counts. Skipped
  * without HISTMERSION_TEST_DB_URL.
  */
class ListFiltersHttpSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(90, "s")

  private lazy val cfg = { val b = AppConfig.load(); b.copy(db = b.db.copy(url = testDb.get)) }
  private lazy val ds = testPool(cfg.db)
  private val tag = s"zzlh${System.nanoTime().toHexString.takeRight(8)}"

  private def client() = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
  private def get(url: String): (Int, String, Map[String, String]) =
    val r = client().send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
    val hs = r.headers.map
    val m = hs.keySet.toArray(Array.empty[String]).map(k => k.toLowerCase -> hs.get(k).get(0)).toMap
    (r.statusCode, r.body, m)
  private def mcp(url: String, tool: String, args: String): String =
    client()
      .send(
        HttpRequest
          .newBuilder(URI.create(s"$url/mcp"))
          .header("content-type", "application/json")
          .header("accept", "application/json, text/event-stream")
          .POST(
            HttpRequest.BodyPublishers.ofString(
              s"""{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"$tool","arguments":$args}}"""
            )
          )
          .build(),
        HttpResponse.BodyHandlers.ofString()
      )
      .body

  private def facets(h: Map[String, String]): Map[String, Map[String, Int]] =
    h.get(ListFilters.FacetsHeader.toLowerCase)
      .fold(Map.empty)(ListFilters.decodeFacets(_).map((f, vs) => f -> vs.toMap).toMap)

  override def afterSuite(): Unit =
    if testDb.nonEmpty then
      transact(ds):
        sql"DELETE FROM approvals WHERE subject LIKE ${tag + "%"}".update.run()
        sql"DELETE FROM jobs WHERE id LIKE ${tag + "%"}".update.run()
      ()

  private def withServer(body: String => Unit): Unit =
    supervised {
      val b = Composition.server(cfg, "127.0.0.1", 0).start()
      try body(s"http://127.0.0.1:${b.port}")
      finally b.stop()
    }

  private def fixtures(): Unit =
    transact(ds):
      List(("goal-plan", "open"), ("goal-plan", "open"), ("article-publish", "open"), ("edge", "approved")).zipWithIndex
        .foreach { case ((kind, state), i) =>
          sql"""INSERT INTO approvals (kind, citekey, subject, proposal, state)
                VALUES ($kind, ${Option.when(kind == "edge")(
              s"${tag}_ck"
            )}, ${s"$tag-s$i"}, '{}'::jsonb, $state)""".update.run()
        }
      List(
        ("review-article", "done", "codex:gpt-x"),
        ("review-article", "failed", "claude-code:opus"),
        ("enrich-source", "done", "codex:gpt-x")
      ).zipWithIndex.foreach { case ((p, st, model), i) =>
        val id = s"$tag-run$i"
        sql"""INSERT INTO jobs (id, kind, status, pipeline, target_id, origin)
                VALUES ($id, ${Agents.JobKind}, $st, $p, ${s"$tag-target$i"}, 'person')""".update.run()
        sql"""INSERT INTO agent_steps (job_id, seq, attempt, name, kind, status, model)
                VALUES ($id, 1, 1, 'x', 'llm', 'done', $model)""".update.run()
      }
      List("ingest", "ingest", "media-import").zipWithIndex.foreach { (k, i) =>
        sql"""INSERT INTO jobs (id, kind, status, citekey) VALUES (${s"$tag-job$i"}, $k, 'done', ${s"${tag}_job$i"})""".update
          .run()
      }

  test("approvals, runs and jobs: filtered on the server, the facets in their header, a bad value refused"):
    withServer { url =>
      fixtures()
      val (s1, b1, h1) = get(s"$url/api/agents/approvals?subject=$tag&kind=goal-plan")
      assertEquals(s1, 200, b1)
      assert(
        b1.startsWith("[") && b1.split("\"kind\":\"goal-plan\"").length == 3 && !b1.contains("article-publish"),
        b1.take(400)
      )
      assertEquals(h1(Paging.TotalHeader.toLowerCase), "2")
      // the kind counts are taken under the state (open) but not the kind filter
      assertEquals(facets(h1)("kind"), Map("goal-plan" -> 2, "article-publish" -> 1))
      assertEquals(Paging.decodeCounts(h1(Paging.CountsHeader.toLowerCase)).toMap, Map("open" -> 2))
      val (_, ball, hall) = get(s"$url/api/agents/approvals?subject=$tag&state=all&kind=goal-plan,edge")
      assertEquals(hall(Paging.TotalHeader.toLowerCase), "3", ball.take(300))
      assertEquals(facets(hall)("decidedBy"), Map("person" -> 1))
      val (bad, bb, _) = get(s"$url/api/agents/approvals?kind=bogus")
      assertEquals(bad, 400, bb)
      assert(bb.contains("unknown kind 'bogus'") && bb.contains("goal-plan"), bb)

      val (s2, b2, h2) = get(s"$url/api/agents/runs?subject=$tag&pipeline=review-article")
      assertEquals(s2, 200, b2)
      assertEquals(h2(Paging.TotalHeader.toLowerCase), "2", b2.take(300))
      assertEquals(facets(h2)("pipeline"), Map("review-article" -> 2, "enrich-source" -> 1))
      assertEquals(facets(h2)("provider"), Map("codex" -> 1, "claude-code" -> 1))
      val (_, b3, h3) = get(s"$url/api/agents/runs?subject=$tag&provider=codex&status=done")
      assertEquals(h3(Paging.TotalHeader.toLowerCase), "2", b3.take(300))
      assertEquals(get(s"$url/api/agents/runs?since=someday")._1, 400)
      assertEquals(get(s"$url/api/agents/runs?pipeline=nope")._1, 400)

      val (s4, b4, h4) = get(s"$url/api/jobs?subject=$tag&kind=ingest")
      assertEquals(s4, 200, b4)
      assertEquals(h4(Paging.TotalHeader.toLowerCase), "2", b4.take(300))
      // the kind facet over this run's jobs: its two ingests, its media import and its three agent runs
      assertEquals(facets(h4)("kind"), Map("ingest" -> 2, "media-import" -> 1, "agent" -> 3))
      assertEquals(get(s"$url/api/jobs?status=stuck")._1, 400)
      assertEquals(get(s"$url/api/jobs?status=waiting&limit=1")._1, 200)

      // MCP: the same filters, and the counts in the Markdown
      val ma = mcp(url, "list_approvals", s"""{"subject":"$tag","kind":["goal-plan"]}""")
      assert(ma.contains("By kind: goal-plan 2 · article-publish 1"), ma)
      val mr = mcp(url, "run_status", s"""{"subject":"$tag","pipeline":["enrich-source"]}""")
      assert(
        mr.contains(s"$tag-run2") && !mr.contains(s"$tag-run0") && mr.contains(
          "By pipeline: review-article 2 · enrich-source 1"
        ),
        mr
      )
      val mj = mcp(url, "job_status", s"""{"subject":"$tag","kind":["media-import"]}""")
      assert(mj.contains(s"$tag-job2") && !mj.contains(s"$tag-job0") && mj.contains("By kind:"), mj)
      val mbad = mcp(url, "list_approvals", """{"decidedBy":["robot"]}""")
      assert(mbad.contains("unknown decidedBy 'robot'"), mbad)
    }
