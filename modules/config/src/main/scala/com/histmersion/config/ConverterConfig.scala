package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The OCR sidecar (plan §1.8). `dpi`/`maxImagePx` are passed through to `POST /convert`; `spoolDir` is where the
  * server keeps an uploaded original so `reocr` can re-convert it without a second upload.
  */
final case class ConverterConfig(
    url: ConverterConfig.Url,
    timeoutSeconds: ConverterConfig.TimeoutSeconds,
    parallelism: ConverterConfig.Parallelism,
    dpi: ConverterConfig.Dpi,
    maxImagePx: ConverterConfig.MaxImagePx,
    spoolDir: ConverterConfig.SpoolDir,
    /** How long `/api/health` waits for the sidecar before reporting it degraded. A dead converter used to block the
      * health check for the full connect+read budget (~15 s), which is exactly when a health check most needs to
      * answer.
      */
    healthTimeoutMs: ConverterConfig.HealthTimeoutMs,
    /** …and how long that answer is reused, so a monitor polling every second does not open a connection per poll.
      */
    healthCacheMs: ConverterConfig.HealthCacheMs,
    /** A conversion may take `pages × this` when that is longer than `timeoutSeconds` — a whole book legitimately runs
      * for an hour. `secondsPerPageQuality` is the docling (quality) path.
      */
    secondsPerPage: ConverterConfig.SecondsPerPage,
    secondsPerPageQuality: ConverterConfig.SecondsPerPageQuality,
    /** A page with no text layer is OCR'd on the fast path too: a scanned 155-page book took over 600 s, more than 3.9
      * s a page.
      */
    secondsPerScannedPage: ConverterConfig.SecondsPerScannedPage
) derives ConfigReader

object ConverterConfig:
  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Parallelism = Parallelism.T
  object Parallelism extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Dpi = Dpi.T
  object Dpi extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxImagePx = MaxImagePx.T
  object MaxImagePx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SpoolDir = SpoolDir.T
  object SpoolDir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HealthTimeoutMs = HealthTimeoutMs.T
  object HealthTimeoutMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HealthCacheMs = HealthCacheMs.T
  object HealthCacheMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SecondsPerPage = SecondsPerPage.T
  object SecondsPerPage extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SecondsPerPageQuality = SecondsPerPageQuality.T
  object SecondsPerPageQuality extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SecondsPerScannedPage = SecondsPerScannedPage.T
  object SecondsPerScannedPage extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
