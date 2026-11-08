package com.histmersion
package web

import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** What a Places or Pictures tab shows: its rows, how many there are in all (some only counted by the map, which lists
  * them when zoomed in), and a line saying where they are from.
  */
final case class MapListState(rows: List[MapListRow], total: MapListState.Total, note: MapListState.Note)

object MapListState:
  type Total = Total.T
  object Total extends RefinedType[Int, Pure]
  type Note = Note.T
  object Note extends RefinedType[String, Pure]
