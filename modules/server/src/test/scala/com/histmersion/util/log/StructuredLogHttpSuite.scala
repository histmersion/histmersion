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

/** The structured log over real HTTP: every answer names its trace, the request is found by it (route template, status,
  * client address, search text, the client's label), a problem id nobody raised is a 404, and a secret seeded through
  * the settings never reaches the table or the file. Gated on HISTMERSION_TEST_DB_URL.
  */
class StructuredLogHttpSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val Secret = "sk-structured-log-secret-7f3a9c"
  private val http = HttpClient.newHttpClient()

  private def get(url: String, headers: (String, String)*): HttpResponse[String] =
    val b = HttpRequest.newBuilder(URI.create(url)).header("Authorization", s"Bearer $Secret")
    http.send(headers.foldLeft(b)((r, h) => r.header(h._1, h._2)).build(), HttpResponse.BodyHandlers.ofString())

  private def page(body: String): LogPage = RawJson.decode[LogPage](body).fold(e => fail(s"$e: $body"), identity)

  /** The log page of `query`, once it holds an event `found` accepts (the writer batches every 200 ms). */
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
      .setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
      .modify(_.log)
      .setTo(base.log.modify(_.dir).setTo(LogConfig.Dir(dir.toString)))
      .modify(_.auth)
      .setTo(base.auth.modify(_.apiKey).setTo(AuthConfig.ApiKey(Secret)))
    supervised {
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0)).start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        val searched = get(s"$url/api/search?q=bronze%20age%20collapse", RequestLog.LabelHeader -> "suite-session")
        val trace = searched.headers().firstValue(RequestLog.TraceHeader).orElse("")
        assert(LogQuery.TraceShape.matches(trace), s"the answer's trace: '$trace'")
        val p = awaitLog(url, s"trace=$trace", _.what.isInstanceOf[What.RequestAnswered])
        val answered = p.events.collect { case LogEvent(_, _, _, label, _, _, r: What.RequestAnswered) => (label, r) }
        assertEquals(answered.size, 1, p.toString)
        val (label, r) = answered.head
        assertEquals(r.operation.value, "/api/search")
        assertEquals(r.method.value, "GET")
        assertEquals(r.status.value, searched.statusCode)
        assertEquals(r.client, Some("127.0.0.1"))
        assertEquals(r.search, Some("bronze age collapse"))
        assertEquals(label, Some("suite-session"))
        // the label alone finds it too
        val byLabel = awaitLog(url, "label=suite-session", _.trace.value == trace)
        assert(byLabel.events.exists(_.trace.value == trace), byLabel.toString)
        // a path parameter is named, never its value
        val job = get(s"$url/api/jobs/no-such-job-77")
        val jobTrace = job.headers().firstValue(RequestLog.TraceHeader).orElse("")
        val jp = awaitLog(url, s"trace=$jobTrace", _.what.isInstanceOf[What.RequestAnswered])
        val ops = jp.events.collect { case LogEvent(_, _, _, _, _, _, r: What.RequestAnswered) => r.operation.value }
        assert(ops.nonEmpty && ops.forall(o => o.contains("{") && !o.contains("no-such-job-77")), ops.toString)
        // the health check is counted, not logged one by one
        val health = get(s"$url/api/health")
        val healthTrace = health.headers().firstValue(RequestLog.TraceHeader).orElse("")
        Thread.sleep(500)
        assertEquals(page(get(s"$url/api/log?trace=$healthTrace").body).events, Vector.empty)
        // a problem id nobody raised
        assertEquals(get(s"$url/api/problems/nobody00").statusCode, 404)
        assertEquals(get(s"$url/api/log?kind=Nope").statusCode, 400)
      finally b.stop()
    }
    // the secret, sent with every request and kept in the settings, is in neither store
    val ds = testPool(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
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
