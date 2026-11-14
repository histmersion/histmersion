package com.histmersion
package server
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.util.ocr.ConverterClient

import com.histmersion.api.Paging
import com.histmersion.config.*
import com.histmersion.domain.*
import ox.supervised

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.{Files, Path}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import com.histmersion.api.library.Ingest
import com.histmersion.api.ocr.{ConvertedDoc, ConvertedImage}
import com.histmersion.db.TestDb
import com.histmersion.util.storage.Blob
import com.histmersion.api.media.ImageSize
import com.histmersion.config.DbConfig
import com.histmersion.config.ServerConfig
import com.histmersion.config.ConverterConfig
import com.histmersion.api.automation.Activity
import com.histmersion.config.Limits

/** The whole §1.8 path over real HTTP, with the converter STUBBED: upload → job → body + assets → the bytes served with
  * the right headers → the figure rendered as a lazy `<img>` → the absolute URLs MCP hands out. Skipped without
  * HISTMERSION_TEST_DB_URL.
  *
  * The stub is the point of `ConverterClient` being a trait: this suite proves the plumbing without a 5 GB OCR
  * container, and the real sidecar is verified end to end separately.
  */
class IngestHttpSuite extends munit.FunSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(90, "s")

  /** A client per REQUEST, HTTP/1.1.
    *
    * HTTP/1.1 because java.net.http's HTTP/2 default mangles body-carrying requests against this server; one connection
    * per request because a ZERO-LENGTH body (`post` with no JSON, an empty upload) can cost the NEXT request on its
    * connection its own body — see [[ZeroLengthUploadSuite]], which is where that behaviour is deliberately exercised.
    * This suite is about the ingest surface, so it takes connection reuse out of the picture entirely. It is also what
    * the CLI does in practice: one process, one command, one connection.
    */
  private def client() = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

  private def send(req: HttpRequest): (Int, String) =
    val r = client().send(req, HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)

  private def get(url: String) = send(HttpRequest.newBuilder(URI.create(url)).build())

  private def bytes(url: String): (Int, Array[Byte], Map[String, String]) =
    val r = client().send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofByteArray())
    val hs = r.headers.map.entrySet.iterator
      .asInstanceOf[java.util.Iterator[java.util.Map.Entry[String, java.util.List[String]]]]
    val m = Iterator
      .continually(if hs.hasNext then Some(hs.next()) else None)
      .takeWhile(_.isDefined)
      .flatten
      .map(e => e.getKey.toLowerCase -> e.getValue.get(0))
      .toMap
    (r.statusCode, r.body, m)

  private def upload(url: String, file: Path) =
    send(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("content-type", "application/octet-stream")
        .POST(HttpRequest.BodyPublishers.ofFile(file))
        .build()
    )

  private def post(url: String, body: String = "") =
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

  private def field(json: String, name: String): Option[String] =
    s""""$name":"([^"]*)"""".r.findFirstMatchIn(json).map(_.group(1))

  // ── the stub ────────────────────────────────────────────────────────────────────────────────
  private val figure: Array[Byte] = Array[Byte](0x89.toByte, 'P', 'N', 'G', 13, 10, 26, 10, 42)
  private val figureB64 = ConvertedImage.Base64(java.util.Base64.getEncoder.encodeToString(figure))

  /** Returns a body that links one figure the canonical way, plus that figure twice — the second copy has identical
    * bytes, so storage must deduplicate it.
    */
  final private class FakeConverter(fail: Option[Problem] = None, beforeConvert: () => Unit = () => ())
      extends ConverterClient:
    @volatile var calls: List[(ConverterClient.Filename, ConverterClient.HighQuality)] = Nil
    def health = Right(ConverterClient.HealthSummary("ok"))
    def convert(file: Blob, filename: ConverterClient.Filename, quality: ConverterClient.HighQuality) =
      beforeConvert()
      calls = calls :+ (filename, quality)
      fail.toLeft(
        ConvertedDoc(
          markdown = ConvertedDoc.Markdown("# Converted\n\nBefore.\n\n![Figure 1](assets/fig-1.png)\n\nAfter.\n"),
          pages = ConvertedDoc.Pages(3),
          images = List(
            ConvertedImage(
              ConvertedImage.Name("fig-1.png"),
              ConvertedImage.Mime("image/png"),
              figureB64,
              Some(ImageSize.Width(120)),
              Some(ImageSize.Height(80))
            ),
            ConvertedImage(
              ConvertedImage.Name("fig-1-copy.png"),
              ConvertedImage.Mime("image/png"),
              figureB64,
              Some(ImageSize.Width(120)),
              Some(ImageSize.Height(80))
            )
          ),
          filename = Some(ConvertedDoc.Filename(filename.value))
        )
      )

  private def tempPdf(marker: String): Path =
    val p = Files.createTempFile("histmersion-ingest-", ".pdf")
    Files.write(p, s"%PDF-1.4 $marker".getBytes("UTF-8"))
    p

  private def cfg =
    val base = AppConfig.load()
    base
      .modify(_.db)
      .setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
      .modify(_.server)
      .setTo(base.server.modify(_.publicUrl).setTo(ServerConfig.PublicUrl("http://histmersion.test")))
      .modify(_.converter)
      .setTo(
        base.converter
          .modify(_.spoolDir)
          .setTo(ConverterConfig.SpoolDir(Files.createTempDirectory("histmersion-spool").toString))
      )

  /** Poll until the job leaves queued/running. */
  private def await(url: String, id: String): String =
    def go(n: Int): String =
      val (_, b) = get(s"$url/api/jobs/$id")
      val st = field(b, "status").getOrElse("")
      if st == "done" || st == "failed" || n > 300 then b
      else
        Thread.sleep(100)
        go(n + 1)
    go(0)

  private val ck = "zz_ingest"
  private val dup = "zz_ingest_dup"

  test("ingest → job → body + assets → served bytes → lazy <img> → absolute MCP URLs"):
    val conv = FakeConverter()
    supervised {
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0), conv).start()
      val url = s"http://127.0.0.1:${b.port}"
      val pdf = tempPdf("one")

      // ── the upload is accepted, not awaited (202 + a job id) ─────────────────────────────────
      val (is, ib) = upload(s"$url/api/ingest?citekey=$ck&title=Converted+paper&year=2011&force=true", pdf)
      assertEquals(is, 202, ib)
      val jobId = field(ib, "jobId").get
      assert(field(ib, "sha256").exists(_.length == 64), ib)

      val done = await(url, jobId)
      assertEquals(field(done, "status"), Some("done"), done)
      assert(done.contains("3 page(s)"), done)

      // the converter saw the original file name and the fast path
      assertEquals(conv.calls.map(_._2), List(false))

      // ── the body kept the RELATIVE link; the identical second figure was deduplicated ────────
      val (gs, gb) = get(s"$url/api/sources/$ck")
      assertEquals(gs, 200)
      assert(gb.contains("assets/fig-1.png"), gb.take(600))
      assert(!gb.contains("/source/zz_ingest/assets"), "storage stays relative: " + gb.take(600))

      val (as, ab) = get(s"$url/api/sources/$ck/assets")
      assertEquals(as, 200)
      assert(ab.contains("fig-1.png"), ab)
      assert(!ab.contains("fig-1-copy.png"), "identical bytes must be stored once: " + ab)

      // ── the bytes, with Content-Type and an immutable cache policy ───────────────────────────
      val (bs, bb, hs) = bytes(s"$url/source/$ck/assets/fig-1.png")
      assertEquals(bs, 200)
      assertEquals(bb.toList, figure.toList)
      assertEquals(hs.get("content-type"), Some("image/png"))
      assertEquals(hs.get("cache-control"), Some(Ingest.AssetCacheControl))
      assertEquals(bytes(s"$url/source/$ck/assets/missing.png")._1, 404)

      // ── the article page renders it as a lazy <img> pointing at the served path ──────────────
      val (ps, pb) = get(s"$url/source/$ck?full=true&fragment=true")
      assertEquals(ps, 200)
      assert(pb.contains("""loading="lazy""""), pb.take(1200))
      assert(pb.contains(s"""src="/source/$ck/assets/fig-1.png""""), pb.take(1200))

      // ── MCP get_source(fullText) hands out ABSOLUTE URLs ─────────────────────────────────────
      val (ms, mb) = mcp(url, "get_source", s"""{"citekey":"$ck","fullText":true}""")
      assertEquals(ms, 200, mb)
      assert(mb.contains(s"http://histmersion.test/source/$ck/assets/fig-1.png"), mb.take(1500))

      // ── MCP assets + job_status ─────────────────────────────────────────────────────────────
      assert(mcp(url, "assets", s"""{"citekey":"$ck"}""")._2.contains("fig-1.png"))
      assert(mcp(url, "job_status", s"""{"id":"$jobId"}""")._2.contains("done"))

      Files.deleteIfExists(pdf)
      b.stop()
    }

  test("a duplicate upload is refused with the citekey that already holds it, unless forced"):
    supervised {
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0), FakeConverter()).start()
      val url = s"http://127.0.0.1:${b.port}"
      val pdf = tempPdf("dupe")

      // the digest is stored by the JOB, so the first upload has to finish before a duplicate of it
      // can be recognised — wait for the job rather than for a wall-clock guess
      val first = field(upload(s"$url/api/ingest?citekey=$ck&force=true", pdf)._2, "jobId").get
      assertEquals(field(await(url, first), "status"), Some("done"))

      val (cs, cb) = upload(s"$url/api/ingest?citekey=$dup", pdf)
      assertEquals(cs, 409, cb)
      assert(cb.contains(ck), cb)

      assertEquals(upload(s"$url/api/ingest?citekey=$dup&force=true", pdf)._1, 202)
      // re-ingesting under the SAME citekey is never a conflict
      assertEquals(upload(s"$url/api/ingest?citekey=$ck", pdf)._1, 202)

      Files.deleteIfExists(pdf)
      b.stop()
    }

  test("re-OCR replaces body and figures but preserves enrichment"):
    supervised {
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0), FakeConverter()).start()
      val url = s"http://127.0.0.1:${b.port}"
      val pdf = tempPdf("reocr")

      val first = field(upload(s"$url/api/ingest?citekey=$ck&force=true", pdf)._2, "jobId").get
      assertEquals(field(await(url, first), "status"), Some("done"))

      // an agent enriches the source
      assertEquals(patch(s"$url/api/sources/$ck", """{"tags":{"add":["sea-peoples"]},"summary":"mine"}""")._1, 200)

      val (rs, rb) = post(s"$url/api/sources/$ck/reocr?quality=true")
      assertEquals(rs, 202, rb)
      assertEquals(field(await(url, field(rb, "jobId").get), "status"), Some("done"), rb)

      val (_, gb) = get(s"$url/api/sources/$ck")
      assert(gb.contains("sea-peoples"), "re-OCR must not wipe enrichment: " + gb.take(600))
      assert(gb.contains("mine"), gb.take(600))
      assert(gb.contains("assets/fig-1.png"), gb.take(600))

      // an unknown citekey is 404; a source with no stored original is a readable 400
      assertEquals(post(s"$url/api/sources/zz_no_such/reocr")._1, 404)

      Files.deleteIfExists(pdf)
      b.stop()
    }

  test("a converter failure fails the job with the reason, and lists under status=failed"):
    supervised {
      val b =
        Composition
          .server(
            cfg,
            ServerConfig.Host("127.0.0.1"),
            ServerConfig.Port(0),
            FakeConverter(Some(Problem.Unclassified(Legacy.Text("converter at http://x is unreachable"))))
          )
          .start()
      val url = s"http://127.0.0.1:${b.port}"
      val pdf = tempPdf("boom")

      val id = field(upload(s"$url/api/ingest?citekey=zz_ingest_fail&force=true", pdf)._2, "jobId").get
      val j = await(url, id)
      assertEquals(field(j, "status"), Some("failed"), j)
      assert(j.contains("unreachable"), j)

      val (ls, lb) = get(s"$url/api/jobs?status=failed&limit=5")
      assertEquals(ls, 200)
      assert(lb.contains(id), lb.take(500))
      assertEquals(get(s"$url/api/jobs/no-such-job")._1, 404)

      // keyset paging (core Paging): the body stays an array; the headers say what lies beyond it
      val (ps, pb, ph) = bytes(s"$url/api/jobs?status=failed&limit=1")
      assertEquals(ps, 200)
      assert(new String(pb, "UTF-8").startsWith("["), new String(pb, "UTF-8").take(200))
      val failed = ph(Paging.TotalHeader.toLowerCase).toInt
      assert(failed >= 1, ph.toString)
      val counts = Paging.decodeCounts(Activity.Counts.Wire(ph(Paging.CountsHeader.toLowerCase)))
      assertEquals(counts.find(_._1.value == "failed").map(_._2), Some(failed), ph.toString)
      assertEquals(ph.contains(Paging.NextHeader.toLowerCase), failed > 1, ph.toString)
      // the id is a cursor: down to it (upTo) holds it; older than it (before) does not
      val (_, upTo, _) = bytes(s"$url/api/jobs?upTo=$id&limit=500")
      assert(new String(upTo, "UTF-8").contains(id))
      val (_, older, _) = bytes(s"$url/api/jobs?before=$id&limit=500")
      assert(!new String(older, "UTF-8").contains(id))
      assertEquals(get(s"$url/api/jobs?before=no-such-job")._1, 400)

      Files.deleteIfExists(pdf)
      b.stop()
    }

  // ── an upload that fulfils an acquisition item ────────────────────────────────────────────────

  /** Add an acquisition item; its id. */
  private def addItem(url: String, text: String, list: String = "acquire"): Int =
    val (st, b) = post(s"$url/api/queue/$list", s"""{"text":"$text","reason":"wanted for the test"}""")
    assertEquals(st, 200, b)
    """"id":(\d+)""".r.findFirstMatchIn(b).get.group(1).toInt

  /** One item as the list returns it (done ones included). */
  private def item(url: String, id: Int, list: String = "acquire"): String =
    val (_, b) = get(s"$url/api/queue/$list?all=true")
    b.split("""\{"id":""").find(_.startsWith(s"$id,")).getOrElse(fail(s"item $id not listed: $b"))

  private def deleteItem(url: String, id: Int) =
    send(HttpRequest.newBuilder(URI.create(s"$url/api/queue/items/$id")).DELETE().build())

  test("an upload for an acquisition item: the JOB ticks it off, only once the source is stored"):
    supervised {
      val converting = CountDownLatch(1)
      val release = CountDownLatch(1)
      val conv = FakeConverter(beforeConvert = () => {
        converting.countDown()
        if !release.await(30, TimeUnit.SECONDS) then throw IllegalStateException("test converter was not released")
      })
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0), conv).start()
      val url = s"http://127.0.0.1:${b.port}"
      val pdf = tempPdf("fulfil")
      val id = addItem(url, "zzfulfil Killen, The New Documents")

      val (is, ib) = upload(s"$url/api/ingest?citekey=zz_fulfil&force=true&acquireItem=$id", pdf)
      assertEquals(is, 202, ib)
      val jobId = field(ib, "jobId").get
      // A 202 alone does not guarantee the worker is still running. Hold the converter until the
      // intermediate state is checked, rather than racing an immediate fake converter.
      try
        assert(converting.await(10, TimeUnit.SECONDS), "worker did not enter the converter")
        assert(item(url, id).contains("\"status\":\"open\""), item(url, id))
      finally release.countDown()

      val done = await(url, jobId)
      assertEquals(field(done, "status"), Some("done"), done)
      assert(done.contains(s"\"acquireItem\":$id"), done)
      assert(done.contains(s"ticked off acquisition item $id"), done)
      val it = item(url, id)
      assert(it.contains("\"status\":\"done\""), it)
      assert(it.contains("wanted for the test · ingested as [[zz_fulfil]]"), it)
      assert(get(s"$url/api/sources/zz_fulfil")._1 == 200)

      // the MCP tool takes the same link, and job_status says which item it ticks off
      val id2 = addItem(url, "zzfulfil a second book")
      val b64 = java.util.Base64.getEncoder.encodeToString("%PDF-1.4 via mcp".getBytes("UTF-8"))
      val (ms, mb) = mcp(
        url,
        "ingest",
        s"""{"citekey":"zz_fulfil_mcp","base64":"$b64","filename":"b.pdf","force":true,"acquireItem":$id2}"""
      )
      assertEquals(ms, 200, mb)
      val mcpJob = """queued job `([^`]+)`""".r.findFirstMatchIn(mb).map(_.group(1)).getOrElse(fail(mb))
      assertEquals(field(await(url, mcpJob), "status"), Some("done"))
      assert(mcp(url, "job_status", s"""{"id":"$mcpJob"}""")._2.contains(s"acquisition item:** $id2"))
      assert(item(url, id2).contains("ingested as [[zz_fulfil_mcp]]"), item(url, id2))

      deleteItem(url, id); deleteItem(url, id2)
      Files.deleteIfExists(pdf)
      b.stop()
    }

  test("a failed OCR leaves the acquisition item open"):
    supervised {
      val b = Composition
        .server(
          cfg,
          ServerConfig.Host("127.0.0.1"),
          ServerConfig.Port(0),
          FakeConverter(Some(Problem.Unclassified(Legacy.Text("converter exploded"))))
        )
        .start()
      val url = s"http://127.0.0.1:${b.port}"
      val pdf = tempPdf("fulfil-fail")
      val id = addItem(url, "zzfulfil a book whose scan fails")

      val jobId =
        field(upload(s"$url/api/ingest?citekey=zz_fulfil_fail&force=true&acquireItem=$id", pdf)._2, "jobId").get
      assertEquals(field(await(url, jobId), "status"), Some("failed"))
      val it = item(url, id)
      assert(it.contains("\"status\":\"open\""), it)
      assert(!it.contains("ingested as"), it)

      deleteItem(url, id)
      Files.deleteIfExists(pdf)
      b.stop()
    }

  test("an upload naming an item it cannot fulfil is refused before a job is created"):
    supervised {
      val conv = FakeConverter()
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0), conv).start()
      val url = s"http://127.0.0.1:${b.port}"
      val pdf = tempPdf("fulfil-refused")
      val done = addItem(url, "zzfulfil already got")
      assertEquals(patch(s"$url/api/queue/items/$done", """{"status":"done"}""")._1, 200)
      val task = addItem(url, "zzfulfil a task, not a book", list = "tasks")

      for (bad, why) <- List(
          done -> "is done; this needs open",
          task -> "is tasks; this needs acquire",
          999999 -> "no queue item id '999999'"
        )
      do
        val (st, body) = upload(s"$url/api/ingest?citekey=zz_fulfil_bad&force=true&acquireItem=$bad", pdf)
        assertEquals(st, 400, body)
        assert(body.contains(why), body)
      assertEquals(conv.calls, Nil, "no job ran")

      deleteItem(url, done); deleteItem(url, task)
      Files.deleteIfExists(pdf)
      b.stop()
    }

  test("empty and oversized uploads are refused before a job is created"):
    supervised {
      val b =
        Composition
          .server(
            cfg.copy(limits = cfg.limits.copy(uploadBytes = Limits.UploadBytes(4))),
            ServerConfig.Host("127.0.0.1"),
            ServerConfig.Port(0),
            FakeConverter()
          )
          .start()
      val url = s"http://127.0.0.1:${b.port}"
      val empty = Files.createTempFile("histmersion-empty-", ".pdf")
      val big = tempPdf("far too long for a four byte limit")

      val (os, ob) = upload(s"$url/api/ingest?citekey=zz_big", big)
      assertEquals(os, 400, ob)
      assert(ob.contains("upload-bytes"), ob)
      val (es, eb) = upload(s"$url/api/ingest?citekey=zz_empty", empty)
      assertEquals(es, 400, eb)
      assert(eb.contains("empty"), eb)
      assertEquals(upload(s"$url/api/ingest?citekey=", big)._1, 400)

      Files.deleteIfExists(empty); Files.deleteIfExists(big)
      b.stop()
    }

  // #692: a single-worker sidecar does not answer /health while it converts; that is busy, not down
  test("/api/health reports a converter that is mid-conversion as busy (status ok), and a dead one as degraded"):
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    val conv = new ConverterClient:
      def health = Left(Problem.Unclassified(Legacy.Text("converter at http://sidecar did not finish within 5 s")))
      def convert(file: Blob, filename: ConverterClient.Filename, quality: ConverterClient.HighQuality) =
        entered.countDown()
        release.await(30, TimeUnit.SECONDS)
        Right(
          ConvertedDoc(
            markdown = ConvertedDoc.Markdown("# Busy\n"),
            pages = ConvertedDoc.Pages(1),
            images = Nil,
            filename = Some(ConvertedDoc.Filename(filename.value))
          )
        )
    val c = cfg.copy(converter = cfg.converter.copy(healthCacheMs = ConverterConfig.HealthCacheMs(0)))
    supervised {
      val b = Composition.server(c, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0), conv).start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        val (_, idle) = get(s"$url/api/health")
        assertEquals(field(idle, "status"), Some("degraded"), idle)
        assert(!idle.contains("seconds-per-page"), "a health probe timing out is not about the page budget: " + idle)

        val pdf = tempPdf("busy")
        val (_, ib) = upload(s"$url/api/ingest?citekey=zz_ingest_busy&force=true", pdf)
        val jobId = field(ib, "jobId").getOrElse(fail(ib))
        assert(entered.await(30, TimeUnit.SECONDS), "the conversion never started")
        val (hs, busy) = get(s"$url/api/health")
        assertEquals(hs, 200)
        assertEquals(field(busy, "status"), Some("ok"), busy)
        assertEquals(field(busy, "converter"), Some(s"converter busy: job $jobId"), busy)

        release.countDown()
        assertEquals(field(await(url, jobId), "status"), Some("done"))
        assertEquals(field(get(s"$url/api/health")._2, "status"), Some("degraded"))
        Files.deleteIfExists(pdf)
      finally
        release.countDown()
        b.stop()
    }

  // #652: `calibre push --replace` takes the file the server stored at ingest
  test("GET /api/sources/{ck}/original serves the stored original, named <citekey>.<ext>"):
    supervised {
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0), FakeConverter()).start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        val pdf = tempPdf("original")
        val (_, ib) = upload(s"$url/api/ingest?citekey=zz_ingest_orig&filename=paper.pdf&force=true", pdf)
        assertEquals(field(await(url, field(ib, "jobId").get), "status"), Some("done"))
        val (os, ob, oh) = bytes(s"$url/api/sources/zz_ingest_orig/original")
        assertEquals(os, 200)
        assertEquals(ob.toSeq, Files.readAllBytes(pdf).toSeq)
        assertEquals(oh.get("content-disposition"), Some("attachment; filename=\"zz_ingest_orig.pdf\""))
        assertEquals(get(s"$url/api/sources/zz_no_such_source/original")._1, 404)
        Files.deleteIfExists(pdf)
      finally b.stop()
    }

  /** One MCP `tools/call`, over the same server. */
  private def mcp(url: String, tool: String, args: String): (Int, String) =
    send(
      HttpRequest
        .newBuilder(URI.create(s"$url/mcp"))
        .header("content-type", "application/json")
        .header("accept", "application/json, text/event-stream")
        .POST(
          HttpRequest.BodyPublishers
            .ofString(s"""{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"$tool","arguments":$args}}""")
        )
        .build()
    )
