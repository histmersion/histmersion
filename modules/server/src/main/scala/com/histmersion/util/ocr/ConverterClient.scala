package com.histmersion.util.ocr

import com.histmersion.api.ocr.ConvertedDoc
import com.histmersion.util.storage.Blob

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
  def convert(file: Blob, filename: String, quality: Boolean): Either[String, ConvertedDoc]

  /** Sidecar liveness for `/api/health`. */
  def health: Either[String, String]

  /** A thumbnail of an image, no side longer than `maxPx` (the sidecar's Pillow — the native server has no working
    * image codec of its own). A converter that cannot make one says so; the picture is then shown from its own bytes.
    */
  def thumbnail(bytes: Array[Byte], filename: String, maxPx: Int): Either[String, ConverterClient.Thumb] =
    Left("this converter makes no thumbnails")

object ConverterClient:

  /** A thumbnail the sidecar made. */
  final case class Thumb(bytes: Thumb.Bytes, mime: Thumb.Mime, width: Thumb.Width, height: Thumb.Height)

  object Thumb:
    type Bytes = Bytes.T
    object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Mime = Mime.T
    object Mime extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Width = Width.T
    object Width extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Height = Height.T
    object Height extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** What `/inspect` reports: all pages, and how many of them have no text layer. */
  final case class PageCount(pages: PageCount.Pages, scanned: PageCount.Scanned = 0)

  object PageCount:
    type Pages = Pages.T
    object Pages extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Scanned = Scanned.T
    object Scanned extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** How long one conversion may run: the configured timeout, or the pages' budget when the document is long enough for
    * that to be more — a scanned page at the scanned rate (the quality path OCRs everything at its own rate). Unknown
    * pages ⇒ the configured timeout.
    */
  def timeoutFor(cfg: ConverterSettings, count: Option[PageCount], quality: Boolean): Duration =
    val perPage = cfg.perPage.value
    val scaled = count
      .map { c =>
        val scanned = math.max(0, math.min(c.scanned, c.pages)).toLong
        val text = math.max(0, c.pages).toLong - scanned
        if quality then c.pages.toLong * math.max(0, cfg.perPageQuality.value)
        else text * math.max(0, perPage) + scanned * math.max(0, math.max(perPage, cfg.perScannedPage.value))
      }
      .getOrElse(0L)
    Duration.ofSeconds(math.max(math.max(1, cfg.timeout.value).toLong, scaled))
