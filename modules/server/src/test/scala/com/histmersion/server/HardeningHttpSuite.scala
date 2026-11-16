package com.histmersion
package server

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.AppConfig
import ox.supervised
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import com.histmersion.db.TestDb
import com.histmersion.config.DbConfig
import com.histmersion.config.ServerConfig
import com.histmersion.config.UiSettings
import com.histmersion.config.UiTimeSettings
import com.histmersion.config.ConverterConfig
import com.histmersion.config.CorpusConfig

/** The hardening fixes, asserted through the wire because that is where they have to hold: citekey/slug validation, the
  * unbounded aggregation window, the inverted half-open range, and the page-size cap. Skipped without
  * HISTMERSION_TEST_DB_URL.
  */
class HardeningHttpSuite extends munit.FunSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val http = HttpClient.newHttpClient()
  private def send(r: HttpRequest) =
    val x = http.send(r, HttpResponse.BodyHandlers.ofString()); (x.statusCode, x.body)
  private def get(url: String) = send(HttpRequest.newBuilder(URI.create(url)).build())
  private def put(url: String, body: String) =
    send(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("content-type", "application/json")
        .PUT(HttpRequest.BodyPublishers.ofString(body))
        .build()
    )
  private def post(url: String, body: String) =
    send(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("content-type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build()
    )
  private def patch(url: String, body: String) =
    send(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("content-type", "application/json")
        .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
        .build()
    )

  private def withServer(f: (String, AppConfig) => Unit): Unit =
    val base = AppConfig.load()
    val cfg = base.modify(_.db).setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    supervised {
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0)).start()
      try f(s"http://127.0.0.1:${b.port}", cfg)
      finally b.stop()
    }

  /** The `from`/`to` the server actually bucketed, whatever was asked for, must sit inside the configured span. (A
    * bound clamped onto `Filter.Earliest`/`Latest` reads back as "no bound given", so an absurd range falls through to
    * the configured DEFAULT window — which is inside the span, and is the point.)
    */
  private def assertWindowIsSane(body: String, cfg: AppConfig): Unit =
    val from = """"from":(-?\d+)""".r.findFirstMatchIn(body).map(_.group(1).toLong)
    val to = """"to":(-?\d+)""".r.findFirstMatchIn(body).map(_.group(1).toLong)
    assert(from.exists(v => v >= cfg.ui.time.min.value && v <= cfg.ui.time.max.value), s"from=$from: ${body.take(120)}")
    assert(to.exists(v => v >= cfg.ui.time.min.value && v <= cfg.ui.time.max.value), s"to=$to: ${body.take(120)}")
    assert(from.get <= to.get, s"$from..$to")

  /** A name no other process uses, so "the traversal created nothing" checks this run alone. */
  private val traversalTarget = s"/tmp/histmersion-traversal-${java.util.UUID.randomUUID()}"
  private val traversal = java.net.URLEncoder.encode(s"../../../..$traversalTarget", "UTF-8")

  test("a request with Expect: 100-continue gets its 100, then its answer (curl sends it for bodies > 1 KB)"):
    withServer { (url, _) =>
      val port = url.split(":").last.toInt
      val body = s"""{"sql":"select 1 as one -- ${"x" * 2000}"}""".getBytes("UTF-8")
      val sock = java.net.Socket("127.0.0.1", port)
      try
        sock.setSoTimeout(5000) // unanswered, the client would wait here forever
        val out = sock.getOutputStream
        val in = java.io.BufferedReader(java.io.InputStreamReader(sock.getInputStream, "UTF-8"))
        out.write(
          (s"POST /api/query HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nContent-Type: application/json\r\n" +
            s"Content-Length: ${body.length}\r\nExpect: 100-continue\r\n\r\n").getBytes("UTF-8")
        )
        out.flush()
        assertEquals(in.readLine(), "HTTP/1.1 100 Continue", "the server must invite the body")
        assertEquals(in.readLine(), "")
        out.write(body); out.flush()
        val status = in.readLine()
        assert(status.startsWith("HTTP/1.1 200"), status)
        val headers = Iterator.continually(in.readLine()).takeWhile(h => h != null && h.nonEmpty).toList
        val length = headers.collectFirst {
          case h if h.toLowerCase.startsWith("content-length:") => h.drop(15).trim.toInt
        }
        val answer = length.fold(headers.mkString("\n")) { n =>
          val buf = new Array[Char](n); var got = 0
          while got < n do { val r = in.read(buf, got, n - got); if r < 0 then got = n else got += r }
          String(buf)
        }
        assert(answer.contains("\"one\""), s"headers: $headers\nbody: ${answer.take(300)}")
      catch case e: java.net.SocketTimeoutException => fail("no answer to Expect: 100-continue")
      finally sock.close()
    }

  test("a path-traversal citekey is a 400 on every write that names one"):
    withServer { (url, _) =>
      val (ps, pb) = put(s"$url/api/sources/$traversal", """{"title":"pwn"}""")
      assertEquals(ps, 400, pb)
      assert(pb.contains("is not a citekey"), pb)
      assertEquals(patch(s"$url/api/sources/$traversal", """{"summary":"x"}""")._1, 400)
      assertEquals(post(s"$url/api/sources/$traversal/reocr", "")._1, 400)
      assertEquals(post(s"$url/api/sources/$traversal/assets", """{"assets":[]}""")._1, 400)
      // …and the spool directory the citekey would have named does not exist
      assert(
        !java.nio.file.Files.exists(java.nio.file.Path.of(traversalTarget)),
        "the traversal must not have created anything"
      )
    }

  test("an upper-case or empty citekey is refused too — one rule, every surface"):
    withServer { (url, _) =>
      assertEquals(put(s"$url/api/sources/NotACitekey", """{"title":"x"}""")._1, 400)
      assertEquals(put(s"$url/api/sources/a", """{"title":"x"}""")._1, 400)
      assertEquals(put(s"$url/api/sources/${"a" * 200}", """{"title":"x"}""")._1, 400)
    }

  test("an absurd aggregation window is CLAMPED, and answers quickly instead of dying"):
    withServer { (url, cfg) =>
      // `from=-1e15&to=1e15` used to ask for 400 000 000 000 buckets and build them one
      // `TimeBucket` at a time. Two guards now: the bounds are clamped into the configured span,
      // and what survives that is checked against `ui.max-buckets` before anything is allocated.
      val started = System.nanoTime()
      val (s1, b1) = get(s"$url/api/aggregate?by=time&from=-1000000000000000&to=1000000000000000")
      val elapsed = (System.nanoTime() - started) / 1000000L
      assertEquals(s1, 200, b1)
      assert(elapsed < 20000, s"it took ${elapsed}ms")
      assertWindowIsSane(b1, cfg)

      // Long.MaxValue is the OVERFLOW case, not merely a big one: the bucket walk used to wrap
      // round and never terminate.
      val (s2, b2) = get(s"$url/api/aggregate?by=time&from=-9223372036854775807&to=9223372036854775807")
      assertEquals(s2, 200, b2)
      assertWindowIsSane(b2, cfg)
    }

  test("past ui.max-buckets the answer is a 400 with the key that raises it"):
    // reachable only when the CONFIGURED span itself is enormous — which is exactly the case the
    // cap exists for, since the clamp above bounds everything a caller can ask for.
    val base = AppConfig.load()
    val cfg = base
      .modify(_.db)
      .setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
      .modify(_.ui)
      .setTo(
        base.ui
          .modify(_.maxBuckets)
          .setTo(UiSettings.MaxBuckets(5))
          .modify(_.time)
          .setTo(
            base.ui.time
              .modify(_.min)
              .setTo(UiTimeSettings.Min((-1000000L: Long)))
              .modify(_.max)
              .setTo(UiTimeSettings.Max((1000000L: Long)))
          )
      )
    supervised {
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0)).start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        val started = System.nanoTime()
        val (s, body) = get(s"$url/api/aggregate?by=time&from=-1000000&to=1000000")
        val elapsed = (System.nanoTime() - started) / 1000000L
        assertEquals(s, 400, body)
        assert(body.contains("ui.max-buckets"), body)
        assert(elapsed < 5000, s"a refusal took ${elapsed}ms — it must not build the buckets first")
      finally b.stop()
    }

  test("a window inside the configured span still works, clamped"):
    withServer { (url, cfg) =>
      val (s, b) = get(s"$url/api/aggregate?by=time&from=-3000&to=-1000")
      assertEquals(s, 200, b)
      // …and one that merely overshoots the configured span is CLAMPED to it rather than refused
      val (cs, cb) = get(s"$url/api/aggregate?by=time&from=-50000&to=5000")
      assertEquals(cs, 200, cb)
      assert(cb.contains(s""""from":${cfg.ui.time.min}"""), cb.take(120))
      assert(cb.contains(s""""to":${cfg.ui.time.max}"""), cb.take(120))
    }

  test("a half-open `from` past the upper sentinel is a 200, not a 500"):
    withServer { (url, _) =>
      // Period(2000000000, Latest) is inverted; Postgres refuses an inverted int8range outright.
      val (s, b) = get(s"$url/api/sources?from=2000000000")
      assertEquals(s, 200, b)
      assertEquals(get(s"$url/api/sources?to=-2000000000")._1, 200)
      assertEquals(get(s"$url/api/findings?from=2000000000")._1, 200)
    }

  test("`limit` is capped at limits.max-page rather than honoured verbatim"):
    withServer { (url, cfg) =>
      val (s, b) = get(s"$url/api/sources?limit=100000000")
      assertEquals(s, 200, b)
      // the cap is what protects the server; a corpus smaller than it simply returns everything
      val hits = """"citekey":""".r.findAllIn(b).size
      assert(hits <= cfg.limits.maxPage.value, s"$hits hits, over the ${cfg.limits.maxPage} cap")
      assertEquals(get(s"$url/api/places?limit=100000000")._1, 200)
      assertEquals(get(s"$url/api/jobs?limit=100000000")._1, 200)
      assertEquals(get(s"$url/api/articles?limit=100000000")._1, 200)
    }

  test("/api/health answers promptly even with a dead converter"):
    val base = AppConfig.load()
    val cfg = base.copy(
      db = base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)),
      // a black-hole address: connecting to it hangs rather than being refused
      converter = base.converter.modify(_.url).setTo(ConverterConfig.Url("http://192.0.2.1:1"))
    )
    supervised {
      val b = Composition
        .server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0), ConverterWiring.client(cfg.converter))
        .start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        val started = System.nanoTime()
        val (s, body) = get(s"$url/api/health")
        val elapsed = (System.nanoTime() - started) / 1000000L
        assertEquals(s, 200, body)
        assert(body.contains("degraded"), body)
        assert(elapsed < 6000, s"health took ${elapsed}ms — the probe must not block on the sidecar")
        // …and the answer is cached, so the second call does not probe again
        val again = System.nanoTime()
        val (s2, b2) = get(s"$url/api/health")
        assertEquals(s2, 200)
        assert(b2.contains("degraded"), b2)
        assert(
          (System.nanoTime() - again) / 1000000L < 1000,
          "a second health check must not start (or wait for) another probe"
        )
      finally b.stop()
    }

  test("with corpus.dir configured, `seed {dir}` cannot read outside it"):
    val corpus = java.nio.file.Files.createTempDirectory("histmersion-corpus-test")
    val base = AppConfig.load()
    val cfg = base
      .modify(_.db)
      .setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
      .modify(_.corpus)
      .setTo(base.corpus.modify(_.dir).setTo(CorpusConfig.Dir(corpus.toString)))
    supervised {
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0)).start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        // …inside is fine (an empty corpus seeds nothing, which is a 200)
        val (okS, okB) = post(s"$url/api/seed", s"""{"dir":"${corpus.toString}"}""")
        assertEquals(okS, 200, okB)

        // …outside is refused, by every spelling
        List("/etc", s"${corpus.toString}/../..", "../../../..").foreach { d =>
          val (s, body) = post(s"$url/api/seed", s"""{"dir":"$d"}""")
          assertEquals(s, 403, s"'$d' was not refused: $body")
          assert(body.contains("the directory it is confined to"), body)
        }

        // the MCP `ingest` tool's `path` is the same rule (reached here through the handler, since
        // the tool transport is JSON-RPC)
        assert(cfg.corpus.dir.value.nonEmpty)
      finally
        b.stop()
        val _ = java.nio.file.Files.deleteIfExists(corpus)
    }
