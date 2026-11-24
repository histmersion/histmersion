package com.histmersion
package api.media
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}
object AssetRelation:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  val Depicts = "depicts"
  val TakenAt = "taken_at"
  val Illustrates = "illustrates"
  val PageOf = "page_of"
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val all: List[Wire] = List(Wire(Depicts), Wire(TakenAt), Wire(Illustrates), Wire(PageOf))
