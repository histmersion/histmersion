package com.histmersion
private[histmersion] object Ascii:

  def lower(s: String): String =
    val cs = (s).value.toCharArray
    var i = 0
    while i < cs.length do
      val c = cs(i)
      if c >= 'A' && c <= 'Z' then cs(i) = (c + 32).toChar
      i += 1
    (new String(cs))
