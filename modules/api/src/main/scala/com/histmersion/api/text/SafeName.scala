package com.histmersion.api.text
object SafeName:
  type Sanitized = Sanitized.T
  object Sanitized extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The safe name of `raw`; `image.png` when nothing is left. */

  def of(raw: String): com.histmersion.api.text.SafeName.Sanitized =
    val base = (raw).value.replace('\\', '/').split('/').lastOption.getOrElse("")
    val cleaned = base
      .map(c => if c.isLetterOrDigit || c == '.' || c == '_' || c == '-' then c else '-')
      .mkString
      .replaceAll("^-+|-+$", "")
    if cleaned.isEmpty then _root_.com.histmersion.api.text.SafeName.Sanitized("image.png") else _root_.com.histmersion.api.text.SafeName.Sanitized(cleaned)
