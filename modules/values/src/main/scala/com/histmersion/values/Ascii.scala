package com.histmersion.values

/** Lower-casing for the vocabularies' spellings: A–Z only, so it means the same on every platform and in
  * every default locale (what `toLowerCase(Locale.ROOT)` does for them; Scala.js has no `Locale` without a
  * locale library, and `values` takes none). */
private[values] object Ascii:
  def lower(s: String): String =
    val cs = s.toCharArray
    var i  = 0
    while i < cs.length do
      val c = cs(i)
      if c >= 'A' && c <= 'Z' then cs(i) = (c + 32).toChar
      i += 1
    new String(cs)
