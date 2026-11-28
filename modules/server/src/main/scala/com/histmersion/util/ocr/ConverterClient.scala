package com.histmersion
package util.ocr
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.api.ocr.ConvertedDoc
import com.histmersion.util.storage.Blob

import java.time.Duration
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.TwoCases

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
  def convert(
      file: Blob,
      filename: ConverterClient.Filename,
      quality: ConverterClient.HighQuality
  ): Either[Problem, ConvertedDoc]

  /** Sidecar liveness for `/api/health`. */
  def health: Either[Problem, ConverterClient.HealthSummary]

  /** A thumbnail of an image, no side longer than `maxPx` (the sidecar's Pillow — the native server has no working
    * image codec of its own). A converter that cannot make one says so; the picture is then shown from its own bytes.
    */
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def thumbnail(
      bytes: ConverterClient.DocumentBytes,
      filename: ConverterClient.Filename,
      maxPx: Pixels
  ): Either[Problem, ConverterClient.Thumb] =
    Left(Problem.Unclassified(Legacy.Text("this converter makes no thumbnails")))

object ConverterClient:
  type DocumentBytes = DocumentBytes.T
  object DocumentBytes extends Newtype[Array[Byte], Pure]
  type Filename = Filename.T
  object Filename extends Newtype[String, Pure]
  type HealthSummary = HealthSummary.T
  object HealthSummary extends Newtype[String, Pure]
  enum HighQuality derives CanEqual:
    case HighQuality, Fast
  object HighQuality extends TwoCases[HighQuality]:
    extension (highQuality: HighQuality) def highQuality: Boolean = highQuality == HighQuality

  /** A thumbnail the sidecar made. */

  final case class Thumb(bytes: Thumb.Bytes, mime: Thumb.Mime, width: Thumb.Width, height: Thumb.Height)

  object Thumb:
    type Bytes = Bytes.T
    object Bytes extends Newtype[Array[Byte], Pure]

    type Mime = Mime.T
    object Mime extends Newtype[String, Pure]
    type Width = Width.T
    object Width extends Newtype[Int, Pure]
    type Height = Height.T
    object Height extends Newtype[Int, Pure]

  /** What `/inspect` reports: all pages, and how many of them have no text layer. */
  final case class PageCount(pages: PageCount.Pages, scanned: PageCount.Scanned = PageCount.Scanned(0))

  object PageCount:
    type Pages = Pages.T
    object Pages extends Newtype[Int, Pure]
    type Scanned = Scanned.T
    object Scanned extends Newtype[Int, Pure]

  /** How long one conversion may run: the configured timeout, or the pages' budget when the document is long enough for
    * that to be more — a scanned page at the scanned rate (the quality path OCRs everything at its own rate). Unknown
    * pages ⇒ the configured timeout.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def timeoutFor(cfg: ConverterSettings, count: Option[PageCount], quality: HighQuality): Duration =
    val perPage = cfg.perPage.value
    val scaled = count
      .map { c =>
        val pages = c.pages.value
        val scanned = math.max(0, math.min(c.scanned.value, pages)).toLong
        val text = math.max(0, pages).toLong - scanned
        if quality.highQuality then pages.toLong * math.max(0, cfg.perPageQuality.value)
        else text * math.max(0, perPage) + scanned * math.max(0, math.max(perPage, cfg.perScannedPage.value))
      }
      .getOrElse(0L)
    Duration.ofSeconds(math.max(math.max(1, cfg.timeout.value).toLong, scaled))
