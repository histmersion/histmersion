package com.histmersion.api.media
import com.histmersion.api.given
object AssetKind:
  val Figure = "figure"
  val Photo = "photo"
  val Map = "map"
  val Plan = "plan"
  val Scan = "scan"
  val all: List[String] = List(Figure, Photo, Map, Plan, Scan)
