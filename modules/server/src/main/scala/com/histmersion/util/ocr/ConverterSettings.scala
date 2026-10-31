package com.histmersion.util.ocr

/** What the converter client is told by the settings: where the converter is, how a document is rasterised, and how
  * long a conversion may run — in all, and per page of text, per page on the quality path and per scanned page.
  */
final case class ConverterSettings(
    url: ConverterUrl,
    timeout: Seconds,
    dpi: Dpi,
    maxImage: Pixels,
    perPage: Seconds,
    perPageQuality: Seconds,
    perScannedPage: Seconds
)
