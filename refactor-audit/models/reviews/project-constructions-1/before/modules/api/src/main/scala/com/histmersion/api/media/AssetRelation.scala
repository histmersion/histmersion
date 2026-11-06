package com.histmersion.api.media
import com.histmersion.api.given
object AssetRelation:
  type All = All.T
  object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Depicts = "depicts"
  val TakenAt = "taken_at"
  val Illustrates = "illustrates"
  val PageOf = "page_of"
  val all: List[com.histmersion.api.media.AssetRelation.All] = List(Depicts, TakenAt, Illustrates, PageOf)
