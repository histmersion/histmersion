package com.histmersion.util.data.exchange

import io.github.iltotore.iron.*

/** Where a step of an import runs among the others of its stage: lower first. The ranks put a record's rows after the
  * rows they point at, and a rename before the rows that carry the new label.
  */
type ImportRank = ImportRank.T
object ImportRank extends RefinedType[Int, Pure]
