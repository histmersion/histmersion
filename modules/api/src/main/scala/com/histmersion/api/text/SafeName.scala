package com.histmersion.api.text
object SafeName:

  /** The safe name of `raw`; `image.png` when nothing is left. */
  type OfResult = OfResult.T
  object OfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfRaw = OfRaw.T
  object OfRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def of(raw: com.histmersion.api.text.SafeName.OfRaw): com.histmersion.api.text.SafeName.OfResult =
    val base = (raw).value.replace('\\', '/').split('/').lastOption.getOrElse("")
    val cleaned = base
      .map(c => if c.isLetterOrDigit || c == '.' || c == '_' || c == '-' then c else '-')
      .mkString
      .replaceAll("^-+|-+$", "")
    if cleaned.isEmpty then _root_.com.histmersion.api.text.SafeName.OfResult("image.png") else _root_.com.histmersion.api.text.SafeName.OfResult(cleaned)
