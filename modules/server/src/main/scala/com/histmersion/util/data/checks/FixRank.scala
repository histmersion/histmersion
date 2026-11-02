package com.histmersion.util.data.checks

import io.github.iltotore.iron.*

/** Where a safe fix runs among every owner's fixes: lower first. The ranks run the citation and index repairs before
  * the findings' places, those before the places' own repairs (which re-match findings), and the articles' last.
  */
type FixRank = FixRank.T
object FixRank extends RefinedType[Int, Pure]
