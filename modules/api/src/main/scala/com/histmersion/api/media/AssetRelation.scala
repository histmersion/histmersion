package com.histmersion.api.media
import com.histmersion.api.given
object AssetRelation:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Depicts = "depicts"
  val TakenAt = "taken_at"
  val Illustrates = "illustrates"
  val PageOf = "page_of"
  val all: List[com.histmersion.api.media.AssetRelation.Wire] = List(_root_.com.histmersion.api.media.AssetRelation.Wire(Depicts), _root_.com.histmersion.api.media.AssetRelation.Wire(TakenAt), _root_.com.histmersion.api.media.AssetRelation.Wire(Illustrates), _root_.com.histmersion.api.media.AssetRelation.Wire(PageOf))
