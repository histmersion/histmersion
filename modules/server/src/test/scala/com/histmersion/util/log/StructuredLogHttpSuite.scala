package com.histmersion
package util.log

import com.histmersion.testsupport.ComparedByValue.given
import com.augustnagro.magnum.*
import com.histmersion.api.log.{LogEvent, LogPage}
import com.histmersion.api.log.LogEvent.What
import com.histmersion.config.*
import com.histmersion.db.{DbSuite, TestDb}
import com.histmersion.server.Composition
import com.histmersion.util.json.RawJson
import hearth.kindlings.optics.syntax.*
import ox.supervised

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class StructuredLogHttpSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val Key = AuthConfig.ApiKey("sk-structured-log-secret-7f3a9c")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val Secret = s"$Key"
  private val http = HttpClient.newHttpClient()

  private def get(url: String, headers: (String, String)*): HttpResponse[String] =
    val b = HttpRequest.newBuilder(URI.create(url)).header("Authorization", s"Bearer $Secret")
    http.send(headers.foldLeft(b)((r, h) => r.header(h._1, h._2)).build(), HttpResponse.BodyHandlers.ofString())

  private def page(body: String): LogPage = RawJson.decode[LogPage](body).fold(e => fail(s"$e: $body"), identity)

  private def awaitLog(url: String, query: String, found: LogEvent => Boolean, left: Int = 100): LogPage =
    val r = get(s"$url/api/log?$query")
    assertEquals(r.statusCode, 200, r.body)
    val p = page(r.body)
    if p.events.exists(found) || left == 0 then p
    else
      Thread.sleep(100)
      awaitLog(url, query, found, left - 1)

  test("an answer names its trace; the request is found by it, with its route, status, client, search and label"):
    val dir = Files.createTempDirectory("hm-structured-log-http")
    val base = AppConfig.load()
    val cfg = base
      .modify(_.db)
      .setTo(base.db.modify(_.url).setTo(TestDb.dbUrl.get))
      .modify(_.log)
      .setTo(base.log.modify(_.dir).setTo(LogConfig.Dir.at(dir.toString)))
      .modify(_.auth)
      .setTo(base.auth.modify(_.apiKey).setTo(Key))
    supervised {
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0)).start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        val searched = get(s"$url/api/search?q=bronze%20age%20collapse", RequestLog.LabelHeader -> "suite-session")
        val trace = searched.headers().firstValue(RequestLog.TraceHeader).orElse("")
        val expected = LogEvent.Trace.fromHeader(trace)
        assert(expected.exists(_.wellFormed), s"the answer's trace: '$trace'")
        val p = awaitLog(url, s"trace=$trace", _.what.isInstanceOf[What.RequestAnswered])
        val answered = p.events.collect { case LogEvent(_, _, _, label, _, _, r: What.RequestAnswered) => (label, r) }
        assertEquals(answered.size, 1, p.toString)
        val (label, r) = answered.head
        assertEquals(r.operation, "/api/search")
        assertEquals(r.method, "GET")
        assertEquals(r.status, searched.statusCode)
        assertEquals(r.client, Some("127.0.0.1"))
        assertEquals(r.search, Some("bronze age collapse"))
        assertEquals(label, Some("suite-session"))
        val byLabel = awaitLog(url, "label=suite-session", e => expected.contains(e.trace))
        assert(byLabel.events.exists(e => expected.contains(e.trace)), byLabel.toString)
        val job = get(s"$url/api/jobs/no-such-job-77")
        val jobTrace = job.headers().firstValue(RequestLog.TraceHeader).orElse("")
        val jp = awaitLog(url, s"trace=$jobTrace", _.what.isInstanceOf[What.RequestAnswered])
        val ops = jp.events.collect { case LogEvent(_, _, _, _, _, _, r: What.RequestAnswered) => s"${r.operation}" }
        assert(ops.nonEmpty && ops.forall(o => o.contains("{") && !o.contains("no-such-job-77")), ops.toString)
        val health = get(s"$url/api/health")
        val healthTrace = health.headers().firstValue(RequestLog.TraceHeader).orElse("")
        Thread.sleep(500)
        assertEquals(page(get(s"$url/api/log?trace=$healthTrace").body).events, Vector.empty)
        val call = http.send(
          HttpRequest
            .newBuilder(URI.create(s"$url/mcp"))
            .header("Authorization", s"Bearer $Secret")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .POST(
              HttpRequest.BodyPublishers.ofString(
                """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"ping","arguments":{}}}"""
              )
            )
            .build(),
          HttpResponse.BodyHandlers.ofString()
        )
        val mcpTrace = call.headers().firstValue(RequestLog.TraceHeader).orElse("")
        val mp = awaitLog(url, s"trace=$mcpTrace", _.what.isInstanceOf[What.ToolCalled])
        assertEquals(
          mp.events.collect { case LogEvent(_, _, _, _, _, _, t: What.ToolCalled) => t.tool },
          Vector(LogEvent.Tool("ping")),
          s"${call.statusCode} ${call.body}"
        )
        assertEquals(get(s"$url/api/problems/nobody00").statusCode, 404)
        assertEquals(get(s"$url/api/log?kind=Nope").statusCode, 400)
      finally b.stop()
    }
    val ds = testPool(base.db.modify(_.url).setTo(TestDb.dbUrl.get))
    val inTable = connect(ds)(
      sql"SELECT count(*) FROM log_events WHERE event::text LIKE ${s"%$Secret%"} OR coalesce(label, '') LIKE ${s"%$Secret%"}"
        .query[Long]
        .run()
        .head
    )
    assertEquals(inTable, 0L)
    val files = Files.list(dir).iterator().asScala.toList
    assert(files.nonEmpty, s"no log file in $dir")
    val inFiles = files.filter(f => Files.readString(f, UTF_8).contains(Secret))
    assertEquals(inFiles, List.empty[Path])
