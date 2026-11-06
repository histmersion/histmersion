package com.histmersion
private[histmersion] object Ascii:
  type LowerResult = LowerResult.T
  object LowerResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LowerS = LowerS.T
  object LowerS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def lower(s: com.histmersion.Ascii.LowerS): com.histmersion.Ascii.LowerResult =
    val cs = (s).value.toCharArray
    var i = 0
    while i < cs.length do
      val c = cs(i)
      if c >= 'A' && c <= 'Z' then cs(i) = (c + 32).toChar
      i += 1
    new String(cs)
