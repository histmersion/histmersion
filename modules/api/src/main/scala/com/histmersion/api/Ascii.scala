package com.histmersion
package api

import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}
/** Lower-casing that touches only `A`–`Z`, the same on the JVM and in the browser (no locale). */
@movesTo(FeaturePlace.Kernel)
private[api] object Ascii:

  def lower(s: String): String =
    val cs = (s).toCharArray
    var i = 0
    while i < cs.length do
      val c = cs(i)
      if c >= 'A' && c <= 'Z' then cs(i) = (c + 32).toChar
      i += 1
    (new String(cs))
