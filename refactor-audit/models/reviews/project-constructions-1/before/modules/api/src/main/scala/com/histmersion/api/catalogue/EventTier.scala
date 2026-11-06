package com.histmersion.api.catalogue
import com.histmersion.api.given
object EventTier:
  type All = All.T
  object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Attested = "attested"
  val Catalogue = "catalogue"
  val all: List[com.histmersion.api.catalogue.EventTier.All] = List(Attested, Catalogue)
