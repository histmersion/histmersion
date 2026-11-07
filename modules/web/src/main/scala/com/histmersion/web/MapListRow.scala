package com.histmersion.web

import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** One row of the Places or Pictures tab: its key, name, a note, the icon's markup, its page and how a plain click
  * opens it.
  */
final case class MapListRow(
    key: MapListRow.Key,
    label: MapListRow.Label,
    note: MapListRow.Note,
    icon: MapListRow.Icon,
    href: MapListRow.Href,
    open: () => Unit,
    undated: MapListRow.Undated = com.histmersion.web.MapListRow.Undated(false),
    kind: com.histmersion.api.ItemKind = com.histmersion.api.ItemKind.Place
)

object MapListRow:
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Note = Note.T
  object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Icon = Icon.T
  object Icon extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Href = Href.T
  object Href extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Undated = Undated.T
  object Undated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
