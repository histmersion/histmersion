package com.histmersion.api.text
object SafeName:

  /** The safe name of `raw`; `image.png` when nothing is left. */
  def of(raw: String): String =
    val base = raw.replace('\\', '/').split('/').lastOption.getOrElse("")
    val cleaned = base
      .map(c => if c.isLetterOrDigit || c == '.' || c == '_' || c == '-' then c else '-')
      .mkString
      .replaceAll("^-+|-+$", "")
    if cleaned.isEmpty then "image.png" else cleaned
