package com.histmersion.api

/** Lower-casing that touches only `A`–`Z`, the same on the JVM and in the browser (no locale). */
private[api] object Ascii:
  type LowerResult = LowerResult.T
  object LowerResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LowerS = LowerS.T
  object LowerS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def lower(s: com.histmersion.api.Ascii.LowerS): com.histmersion.api.Ascii.LowerResult =
    val cs = (s).value.toCharArray
    var i = 0
    while i < cs.length do
      val c = cs(i)
      if c >= 'A' && c <= 'Z' then cs(i) = (c + 32).toChar
      i += 1
    _root_.com.histmersion.api.Ascii.LowerResult(new String(cs))
