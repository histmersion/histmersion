package com.histmersion.web

import org.scalajs.dom

import scala.scalajs.js

/** A clustered dot that is not a finding — a site, an event, a picture: what the bubble counts and colours, and the row
  * its list shows (its kind's icon, its name, a note, `mid` the middle of its dated span on the period ramp). A
  * finding's dot carries its [[GeoHit]]s instead.
  */
final case class DotEntry(
    kind: DotKind,
    id: DotEntry.Id,
    label: DotEntry.Label,
    icon: DotEntry.Icon,
    mid: Option[DotEntry.Mid],
    note: DotEntry.Note = ""
)

object DotEntry:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Icon = Icon.T
  object Icon extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Mid = Mid.T
  object Mid extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Note = Note.T
  object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
