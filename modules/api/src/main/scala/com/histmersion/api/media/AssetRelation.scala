package com.histmersion
package api.media
import com.histmersion.api.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
object AssetRelation:
  type Wire = Wire.T
  object Wire extends RefinedType[String, Pure]

  val Depicts = "depicts"
  val TakenAt = "taken_at"
  val Illustrates = "illustrates"
  val PageOf = "page_of"
  val all: List[Wire] = List(Wire(Depicts), Wire(TakenAt), Wire(Illustrates), Wire(PageOf))
