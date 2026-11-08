package com.histmersion
package api.media
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
object AssetKind:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  val Figure = "figure"
  val Photo = "photo"
  val Map = "map"
  val Plan = "plan"
  val Scan = "scan"
  val all: List[Wire] = List(Wire(Figure), Wire(Photo), Wire(Map), Wire(Plan), Wire(Scan))
