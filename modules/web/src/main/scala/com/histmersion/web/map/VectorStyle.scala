package com.histmersion
package web.map

import org.scalajs.dom

import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** The two vector styles drawn from the server's own extracts (docs/research-map-styles.md). */
enum VectorStyle(val id: VectorStyle.Id, val label: VectorStyle.Label) derives CanEqual:
  case Readable extends VectorStyle(VectorStyle.Id("readable"), VectorStyle.Label("Readable"))
  case Physical extends VectorStyle(VectorStyle.Id("physical"), VectorStyle.Label("Physical"))

object VectorStyle:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Id): Option[VectorStyle] = values.find(_.id.value == s.value.trim.toLowerCase)
