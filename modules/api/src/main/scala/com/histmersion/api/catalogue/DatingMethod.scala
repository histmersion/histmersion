package com.histmersion.api.catalogue
import com.histmersion.api.given
object DatingMethod:
  type All = All.T
  object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val all: List[com.histmersion.api.catalogue.DatingMethod.All] = List("dendrochronology", "radiocarbon", "stratigraphy", "textual", "astronomical")
