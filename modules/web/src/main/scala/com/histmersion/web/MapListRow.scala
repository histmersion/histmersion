package com.histmersion.web

import com.histmersion.api.*
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** One row of the Places or Pictures tab: its key, name, a note, the icon's markup, its page and how a plain click
  * opens it.
  */
final case class MapListRow(
    key: String,
    label: String,
    note: String,
    icon: String,
    href: String,
    open: () => Unit,
    undated: Boolean = false,
    kind: com.histmersion.api.personal.ItemKind = com.histmersion.api.personal.ItemKind.Place
)
