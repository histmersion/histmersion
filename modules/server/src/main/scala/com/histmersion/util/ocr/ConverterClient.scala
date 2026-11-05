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
  def convert(file: Blob, filename: com.histmersion.util.ocr.ConverterClient.ConvertFilename, quality: com.histmersion.util.ocr.ConverterClient.ConvertQuality): Either[com.histmersion.util.ocr.ConverterClient.ConvertResultPart1, ConvertedDoc]

  /** Sidecar liveness for `/api/health`. */
  def health: Either[com.histmersion.util.ocr.ConverterClient.HealthResultPart1, com.histmersion.util.ocr.ConverterClient.HealthResultPart2]

  /** A thumbnail of an image, no side longer than `maxPx` (the sidecar's Pillow — the native server has no working
    * image codec of its own). A converter that cannot make one says so; the picture is then shown from its own bytes.
    */
  def thumbnail(bytes: Array[Byte], filename: com.histmersion.util.ocr.ConverterClient.ThumbnailFilename, maxPx: com.histmersion.util.ocr.ConverterClient.ThumbnailMaxPx): Either[com.histmersion.util.ocr.ConverterClient.ThumbnailResultPart1, ConverterClient.Thumb] =
    Left("this converter makes no thumbnails")

object ConverterClient:

  /** A thumbnail the sidecar made. */
  type ConvertResultPart1 = ConvertResultPart1.T
  object ConvertResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConvertFilename = ConvertFilename.T
  object ConvertFilename extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConvertQuality = ConvertQuality.T
  object ConvertQuality extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HealthResultPart1 = HealthResultPart1.T
  object HealthResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HealthResultPart2 = HealthResultPart2.T
  object HealthResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbnailResultPart1 = ThumbnailResultPart1.T
  object ThumbnailResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbnailFilename = ThumbnailFilename.T
  object ThumbnailFilename extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbnailMaxPx = ThumbnailMaxPx.T
  object ThumbnailMaxPx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeoutForQuality = TimeoutForQuality.T
  object TimeoutForQuality extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
  def timeoutFor(cfg: ConverterSettings, count: Option[PageCount], quality: com.histmersion.util.ocr.ConverterClient.TimeoutForQuality): Duration =
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
