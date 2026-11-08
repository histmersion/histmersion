package com.histmersion
package web

import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.ItemKind
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
    undated: MapListRow.Undated = MapListRow.Undated(false),
    kind: ItemKind = ItemKind.Place
)

object MapListRow:
  type Key = Key.T
  object Key extends RefinedType[String, Pure]
  type Label = Label.T
  object Label extends RefinedType[String, Pure]
  type Note = Note.T
  object Note extends RefinedType[String, Pure]
  type Icon = Icon.T
  object Icon extends RefinedType[String, Pure]
  type Href = Href.T
  object Href extends RefinedType[String, Pure]
  type Undated = Undated.T
  object Undated extends RefinedType[Boolean, Pure]
