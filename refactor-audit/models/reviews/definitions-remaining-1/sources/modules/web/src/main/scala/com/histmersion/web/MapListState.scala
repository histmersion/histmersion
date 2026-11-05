package com.histmersion.web

import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** What a Places or Pictures tab shows: its rows, how many there are in all (some only counted by the map, which lists
  * them when zoomed in), and a line saying where they are from.
  */
final case class MapListState(rows: List[MapListRow], total: MapListState.Total, note: MapListState.Note)

object MapListState:
  type Total = Total.T
  object Total extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Note = Note.T
  object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
