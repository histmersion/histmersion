package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The OCR sidecar (plan §1.8). `dpi`/`maxImagePx` are passed through to `POST /convert`; `spoolDir` is where the
  * server keeps an uploaded original so `reocr` can re-convert it without a second upload.
  */
final case class ConverterConfig(
    url: String,
    timeoutSeconds: Int,
    parallelism: Int,
    dpi: Int,
    maxImagePx: Int,
    spoolDir: String,
    /** How long `/api/health` waits for the sidecar before reporting it degraded. A dead converter used to block the
      * health check for the full connect+read budget (~15 s), which is exactly when a health check most needs to
      * answer.
      */
    healthTimeoutMs: Int,
    /** …and how long that answer is reused, so a monitor polling every second does not open a connection per poll.
      */
    healthCacheMs: Int,
    /** A conversion may take `pages × this` when that is longer than `timeoutSeconds` — a whole book legitimately runs
      * for an hour. `secondsPerPageQuality` is the docling (quality) path.
      */
    secondsPerPage: Int,
    secondsPerPageQuality: Int,
    /** A page with no text layer is OCR'd on the fast path too: a scanned 155-page book took over 600 s, more than 3.9
      * s a page.
      */
    secondsPerScannedPage: Int
) derives ConfigReader
