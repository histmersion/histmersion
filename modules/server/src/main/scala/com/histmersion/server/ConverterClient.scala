package com.histmersion.server

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import java.nio.file.Path
import java.time.Duration

/** The seam between the server and the Python OCR sidecar (plan §1.8).
  *
  * A trait, so an HTTP test can ingest a PDF through a FAKE converter and still exercise the whole job → assets →
  * rendering path without a container. The real implementation speaks the sidecar's multipart `POST /convert` over
  * `java.net.http` — no extra dependency, and nothing for native-image to reflect over.
  */
trait ConverterClient:
  /** Convert one document. `Left` carries a message that names the converter URL, because "the OCR job failed" without
    * saying where it tried to go is useless in a log.
    */
  def convert(file: Path, filename: String, quality: Boolean): Either[String, ConvertedDoc]

  /** Sidecar liveness for `/api/health`. */
  def health: Either[String, String]

  /** A thumbnail of an image, no side longer than `maxPx` (the sidecar's Pillow — the native server has no working
    * image codec of its own). A converter that cannot make one says so; the picture is then shown from its own bytes.
    */
  def thumbnail(bytes: Array[Byte], filename: String, maxPx: Int): Either[String, ConverterClient.Thumb] =
    Left("this converter makes no thumbnails")

object ConverterClient:

  /** A thumbnail the sidecar made. */
  final case class Thumb(bytes: Array[Byte], mime: String, width: Int, height: Int)

  /** By name: the server passes `deps.cfg.converter`, read at each request. */
  def fromConfig(cfg: => ConverterConfig): ConverterClient = JatsConverter(HttpConverterClient(cfg))

  /** What `/inspect` reports: all pages, and how many of them have no text layer. */
  final case class PageCount(pages: Int, scanned: Int = 0)

  /** How long one conversion may run: the configured timeout, or the pages' budget when the document is long enough for
    * that to be more — a scanned page at the scanned rate (the quality path OCRs everything at its own rate). Unknown
    * pages ⇒ the configured timeout.
    */
  def timeoutFor(cfg: ConverterConfig, count: Option[PageCount], quality: Boolean): Duration =
    val scaled = count
      .map { c =>
        val scanned = math.max(0, math.min(c.scanned, c.pages)).toLong
        val text = math.max(0, c.pages).toLong - scanned
        if quality then c.pages.toLong * math.max(0, cfg.secondsPerPageQuality)
        else
          text * math
            .max(0, cfg.secondsPerPage) + scanned * math.max(0, math.max(cfg.secondsPerPage, cfg.secondsPerScannedPage))
      }
      .getOrElse(0L)
    Duration.ofSeconds(math.max(math.max(1, cfg.timeoutSeconds).toLong, scaled))
