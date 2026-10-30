package com.histmersion.web

import com.histmersion.api.*
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** What a Places or Pictures tab shows: its rows, how many there are in all (some only counted by the map, which lists
  * them when zoomed in), and a line saying where they are from.
  */
final case class MapListState(rows: List[MapListRow], total: Int, note: String)
