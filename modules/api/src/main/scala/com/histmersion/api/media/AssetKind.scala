package com.histmersion.api.media
import com.histmersion.api.given
object AssetKind:
  type All = All.T
  object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Figure = "figure"
  val Photo = "photo"
  val Map = "map"
  val Plan = "plan"
  val Scan = "scan"
  val all: List[com.histmersion.api.media.AssetKind.All] = List(Figure, Photo, Map, Plan, Scan)
