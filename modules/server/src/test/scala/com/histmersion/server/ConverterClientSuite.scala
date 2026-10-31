package com.histmersion.server

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

  private def pc(pages: Int, scanned: Int = 0) = Some(ConverterClient.PageCount(pages, scanned))

  test("the timeout is the configured one, or pages × seconds-per-page when that is longer"):
    val c = base.copy(timeoutSeconds = 600, secondsPerPage = 2, secondsPerPageQuality = 8, secondsPerScannedPage = 15)
    assertEquals(
      ConverterClient.timeoutFor(ConverterWiring.settings(c), pc(10), quality = false),
      Duration.ofSeconds(600)
    )
    assertEquals(
      ConverterClient.timeoutFor(ConverterWiring.settings(c), pc(600), quality = false),
      Duration.ofSeconds(1200)
    )
    assertEquals(
      ConverterClient.timeoutFor(ConverterWiring.settings(c), pc(600), quality = true),
      Duration.ofSeconds(4800)
    )
    assertEquals(ConverterClient.timeoutFor(ConverterWiring.settings(c), None, quality = true), Duration.ofSeconds(600))

  test("scanned pages get the scanned rate: the 155-page scan that failed at 600 s now gets 2325 s"):
    val c = base.copy(timeoutSeconds = 600, secondsPerPage = 2, secondsPerPageQuality = 8, secondsPerScannedPage = 15)
    assertEquals(
      ConverterClient.timeoutFor(ConverterWiring.settings(c), pc(155, scanned = 155), quality = false),
      Duration.ofSeconds(2325)
    )
    assertEquals(
      ConverterClient.timeoutFor(ConverterWiring.settings(c), pc(400, scanned = 100), quality = false),
      Duration.ofSeconds(300 * 2 + 100 * 15)
    )
    assertEquals(
      ConverterClient.timeoutFor(ConverterWiring.settings(c), pc(10, scanned = 99), quality = false),
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
        ConverterWiring.settings(base.copy(url = url, timeoutSeconds = 1, secondsPerPage = 3)),
        ConverterWiring.http()
      )
        .convert(OfflineConverter.blob(file), "book.pdf", quality = false)
    finally Files.deleteIfExists(file)

  test("a long document gets the scaled time: a 2 s conversion of 1 page at 3 s/page succeeds past a 1 s timeout"):
    withSidecar(pages = Some(1), convertMs = 2000) { url =>
      assertEquals(convert(url).map(_.markdown), Right("# ok"))
    }

  test("an older sidecar without /inspect: the plain timeout applies, and the error says it timed out"):
    withSidecar(pages = None, convertMs = 2000) { url =>
      val r = convert(url)
      assert(r.left.exists(e => e.contains("did not finish within 1 s") && !e.contains("unreachable")), r.toString)
    }

/** #692: what `/api/health` says about the sidecar, from the probe and the job it is converting. */
class ConverterStateSuite extends munit.FunSuite:
  test("a failed probe during a conversion is busy (healthy); without one it is down"):
    assertEquals(ConverterState.of("ok", None), ConverterState.Ok)
    assertEquals(ConverterState.of("ok", Some("j1")), ConverterState.Ok)
    val busy = ConverterState.of("degraded: no answer from the converter within 5000 ms", Some("j1"))
    assertEquals(busy, ConverterState.Busy("j1"))
    assert(busy.healthy)
    assertEquals(busy.render, "converter busy: job j1")
    val down = ConverterState.of("converter at http://x is unreachable", None)
    assert(!down.healthy)
    assertEquals(down.render, "converter at http://x is unreachable")
