package com.histmersion.server

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.util.ocr.{ConverterClient, ConverterState, HttpConverterClient}

import com.histmersion.config.AppConfig
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.time.Duration

/** A whole book legitimately converts for far longer than an article: the client asks the sidecar for the page count
  * and allows `pages × seconds-per-page` when that beats the configured timeout.
  */
class ConverterClientSuite extends munit.FunSuite:

  private val base = AppConfig.load().converter

  private def pc(pages: ConverterClient.PageCount.Pages, scanned: ConverterClient.PageCount.Scanned = ConverterClient.PageCount.Scanned(0)) =
    Some(ConverterClient.PageCount(pages, scanned))

  test("the timeout is the configured one, or pages × seconds-per-page when that is longer"):
    val c = base.modify(_.timeoutSeconds).setTo(com.histmersion.config.ConverterConfig.TimeoutSeconds(600)).modify(_.secondsPerPage).setTo(com.histmersion.config.ConverterConfig.SecondsPerPage(2)).modify(_.secondsPerPageQuality).setTo(com.histmersion.config.ConverterConfig.SecondsPerPageQuality(8)).modify(_.secondsPerScannedPage).setTo(com.histmersion.config.ConverterConfig.SecondsPerScannedPage(15))
    assertEquals(
      ConverterClient.timeoutFor(ConverterWiring.settings(c), pc(ConverterClient.PageCount.Pages(10)), quality = com.histmersion.util.ocr.ConverterClient.HighQuality(false)),
      Duration.ofSeconds(600)
    )
    assertEquals(
      ConverterClient.timeoutFor(ConverterWiring.settings(c), pc(ConverterClient.PageCount.Pages(600)), quality = com.histmersion.util.ocr.ConverterClient.HighQuality(false)),
      Duration.ofSeconds(1200)
    )
    assertEquals(
      ConverterClient.timeoutFor(ConverterWiring.settings(c), pc(ConverterClient.PageCount.Pages(600)), quality = com.histmersion.util.ocr.ConverterClient.HighQuality(true)),
      Duration.ofSeconds(4800)
    )
    assertEquals(ConverterClient.timeoutFor(ConverterWiring.settings(c), None, quality = com.histmersion.util.ocr.ConverterClient.HighQuality(true)), Duration.ofSeconds(600))

  test("scanned pages get the scanned rate: the 155-page scan that failed at 600 s now gets 2325 s"):
    val c = base.modify(_.timeoutSeconds).setTo(com.histmersion.config.ConverterConfig.TimeoutSeconds(600)).modify(_.secondsPerPage).setTo(com.histmersion.config.ConverterConfig.SecondsPerPage(2)).modify(_.secondsPerPageQuality).setTo(com.histmersion.config.ConverterConfig.SecondsPerPageQuality(8)).modify(_.secondsPerScannedPage).setTo(com.histmersion.config.ConverterConfig.SecondsPerScannedPage(15))
    assertEquals(
      ConverterClient.timeoutFor(ConverterWiring.settings(c), pc(ConverterClient.PageCount.Pages(155), scanned = ConverterClient.PageCount.Scanned(155)), quality = com.histmersion.util.ocr.ConverterClient.HighQuality(false)),
      Duration.ofSeconds(2325)
    )
    assertEquals(
      ConverterClient.timeoutFor(ConverterWiring.settings(c), pc(ConverterClient.PageCount.Pages(400), scanned = ConverterClient.PageCount.Scanned(100)), quality = com.histmersion.util.ocr.ConverterClient.HighQuality(false)),
      Duration.ofSeconds(300 * 2 + 100 * 15)
    )
    assertEquals(
      ConverterClient.timeoutFor(ConverterWiring.settings(c), pc(ConverterClient.PageCount.Pages(10), scanned = ConverterClient.PageCount.Scanned(99)), quality = com.histmersion.util.ocr.ConverterClient.HighQuality(false)),
      Duration.ofSeconds(600),
      "scanned is capped at pages"
    )

  /** A stand-in sidecar: `/inspect` reports `pages`, `/convert` answers after `convertMs`. */
  private def withSidecar(pages: Option[Int], convertMs: Long)(f: String => Unit): Unit =
    val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    def answer(ex: com.sun.net.httpserver.HttpExchange, code: Int, body: String): Unit =
      val _ = ex.getRequestBody.readAllBytes()
      val b = body.getBytes("UTF-8")
      ex.sendResponseHeaders(code, b.length.toLong); ex.getResponseBody.write(b); ex.close()
    srv.createContext(
      "/inspect",
      ex => pages.fold(answer(ex, 404, "no such route"))(n => answer(ex, 200, s"""{"pages":$n,"scanned":0}"""))
    )
    srv.createContext(
      "/convert",
      ex => { Thread.sleep(convertMs); answer(ex, 200, """{"markdown":"# ok","pages":3,"images":[]}""") }
    )
    srv.setExecutor(java.util.concurrent.Executors.newCachedThreadPool())
    srv.start()
    try f(s"http://127.0.0.1:${srv.getAddress.getPort}")
    finally srv.stop(0)

  private def convert(url: String) =
    val file = Files.createTempFile("book", ".pdf")
    try
      HttpConverterClient(
        ConverterWiring.settings(base.modify(_.url).setTo(com.histmersion.config.ConverterConfig.Url(url)).modify(_.timeoutSeconds).setTo(com.histmersion.config.ConverterConfig.TimeoutSeconds(1)).modify(_.secondsPerPage).setTo(com.histmersion.config.ConverterConfig.SecondsPerPage(3))),
        ConverterWiring.http()
      )
        .convert(OfflineConverter.blob(file), com.histmersion.util.ocr.ConverterClient.Filename("book.pdf"), quality = com.histmersion.util.ocr.ConverterClient.HighQuality(false))
    finally Files.deleteIfExists(file)

  test("a long document gets the scaled time: a 2 s conversion of 1 page at 3 s/page succeeds past a 1 s timeout"):
    withSidecar(pages = Some(1), convertMs = 2000) { url =>
      assertEquals(convert(url).map(_.markdown), Right("# ok"))
    }

  test("an older sidecar without /inspect: the plain timeout applies, and the error says it timed out"):
    withSidecar(pages = None, convertMs = 2000) { url =>
      val r = convert(url)
      assert(r.left.exists(e => e.value.contains("did not finish within 1 s") && !e.value.contains("unreachable")), r.toString)
    }

/** #692: what `/api/health` says about the sidecar, from the probe and the job it is converting. */
class ConverterStateSuite extends munit.FunSuite:
  test("a failed probe during a conversion is busy (healthy); without one it is down"):
    assertEquals(ConverterState.of(com.histmersion.util.ocr.ConverterState.Probe("ok"), None), ConverterState.Ok)
    assertEquals(ConverterState.of(com.histmersion.util.ocr.ConverterState.Probe("ok"), Some(com.histmersion.util.jobs.Job.Id.assume("j1"))), ConverterState.Ok)
    val busy = ConverterState.of(com.histmersion.util.ocr.ConverterState.Probe("degraded: no answer from the converter within 5000 ms"), Some(com.histmersion.util.jobs.Job.Id.assume("j1")))
    assertEquals(busy, ConverterState.Busy(com.histmersion.util.ocr.ConverterState.Busy.JobId.assume("j1")))
    assert(busy.healthy)
    assertEquals(busy.render, "converter busy: job j1")
    val down = ConverterState.of(com.histmersion.util.ocr.ConverterState.Probe("converter at http://x is unreachable"), None)
    assert(!down.healthy)
    assertEquals(down.render, "converter at http://x is unreachable")
