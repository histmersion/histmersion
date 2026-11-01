package com.histmersion.util.text

/** A file name safe in a link: the last path segment, every character but letters, digits, `.`, `_` and `-` a hyphen,
  * no leading or trailing hyphen. Must match the converter's `_safe_name`: either side may name a file.
  */
object SafeName:

  /** The safe name of `raw`; `image.png` when nothing is left. */
  def of(raw: String): String =
    val base = raw.replace('\\', '/').split('/').lastOption.getOrElse("")
    val cleaned = base
      .map(c => if c.isLetterOrDigit || c == '.' || c == '_' || c == '-' then c else '-')
      .mkString
      .replaceAll("^-+|-+$", "")
    if cleaned.isEmpty then "image.png" else cleaned
