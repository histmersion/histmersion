package com.histmersion.util.text

import java.text.Normalizer

/** The form two texts are compared in when one should be found in the other — an OCR'd body and a model's quote, two
  * reviewers' wordings: NFKC, lower case, ASCII quotes and dashes, ligatures unfolded, soft hyphens dropped, words
  * hyphenated across a line break re-joined, Markdown marks dropped, whitespace collapsed. JVM-only (NFKC).
  */
object ComparedText:

  private val ligatures = List(
    "ﬀ" -> "ff",
    "ﬁ" -> "fi",
    "ﬂ" -> "fl",
    "ﬃ" -> "ffi",
    "ﬄ" -> "ffl",
    "ﬅ" -> "st",
    "ﬆ" -> "st",
    "æ" -> "ae",
    "œ" -> "oe"
  )

  /** The text an OCR'd body and a model's quote are compared in: NFKC (which also unfolds most ligatures), lower case,
    * ASCII quotes and dashes, soft hyphens dropped, words hyphenated across a line break re-joined, Markdown
    * emphasis/heading marks dropped, whitespace collapsed.
    */
  def normalise(s: String): String =
    var t = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(java.util.Locale.ROOT) // local accumulator
    ligatures.foreach((a, b) => t = t.replace(a, b))
    t = t
      .replace("­", "")
      .replaceAll("[‘’‚‛′`]", "'")
      .replaceAll("[“”„‟″«»]", "\"")
      .replaceAll("[‐‑‒–—―−]", "-")
      .replaceAll("(\\w)-[ \\t]*\\r?\\n[ \\t]*(\\w)", "$1$2") // exam-\nple → example
      .replaceAll("[*_#>|]", " ")
      .replaceAll("\\s+", " ")
      .trim
    t
