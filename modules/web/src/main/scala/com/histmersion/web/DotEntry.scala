package com.histmersion
package web

import org.scalajs.dom

import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** A clustered dot that is not a finding — a site, an event, a picture: what the bubble counts and colours, and the row
  * its list shows (its kind's icon, its name, a note, `mid` the middle of its dated span on the period ramp). A
  * finding's dot carries its [[GeoHit]]s instead.
  */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class DotEntry(
    kind: DotKind,
    id: DotEntry.Id,
    label: DotEntry.Label,
    icon: DotEntry.Icon,
    mid: Option[DotEntry.Mid],
    note: DotEntry.Note = DotEntry.Note("")
)

object DotEntry:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Icon = Icon.T
  object Icon extends Newtype[String, Pure]
  type Mid = Mid.T
  object Mid extends Newtype[Double, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
