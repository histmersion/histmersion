package com.histmersion.api.media
import com.histmersion.api.given
object AssetKind:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Figure = "figure"
  val Photo = "photo"
  val Map = "map"
  val Plan = "plan"
  val Scan = "scan"
  val all: List[com.histmersion.api.media.AssetKind.Wire] = List(_root_.com.histmersion.api.media.AssetKind.Wire(Figure), _root_.com.histmersion.api.media.AssetKind.Wire(Photo), _root_.com.histmersion.api.media.AssetKind.Wire(Map), _root_.com.histmersion.api.media.AssetKind.Wire(Plan), _root_.com.histmersion.api.media.AssetKind.Wire(Scan))
