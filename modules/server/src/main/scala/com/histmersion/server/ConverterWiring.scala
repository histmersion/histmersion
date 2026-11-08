package com.histmersion
package server

import com.histmersion.config.ConverterConfig
import com.histmersion.util.ocr.{
  ConverterClient,
  ConverterSettings,
  ConverterUrl,
  Dpi,
  HttpConverterClient,
  JatsConverter,
  Pixels,
  Seconds
}

import java.net.http.HttpClient
import java.time.Duration

/** The OCR converter's client over the settings: what the client is told of `converter.*`, read at each request. */
object ConverterWiring:

  def settings(c: ConverterConfig): ConverterSettings =
    ConverterSettings(
      ConverterUrl(c.url.value),
      Seconds(c.timeoutSeconds.value),
      Dpi(c.dpi.value),
      Pixels(c.maxImagePx.value),
      Seconds(c.secondsPerPage.value),
      Seconds(c.secondsPerPageQuality.value),
      Seconds(c.secondsPerScannedPage.value)
    )

  /** HTTP/1.1 explicitly: java.net.http defaults to HTTP/2 and offers an h2c upgrade (`Connection: Upgrade,
    * HTTP2-Settings` + `Upgrade: h2c`), on which uvicorn DISCARDS the request body — the sidecar then answers 422
    * "field required: file" for a perfectly well-formed upload.
    */
  def http(): HttpClient =
    HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build()

  /** By name: the server passes `deps.cfg.converter`, read at each request. */
  def client(cfg: => ConverterConfig): ConverterClient = JatsConverter(HttpConverterClient(settings(cfg), http()))
