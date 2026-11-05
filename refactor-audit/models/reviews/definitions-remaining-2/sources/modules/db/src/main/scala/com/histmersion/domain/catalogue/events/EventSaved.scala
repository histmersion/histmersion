package com.histmersion.domain.catalogue.events

/** What a write of placed records did: events created, events an import made before refreshed, catalogue rows written,
  * and what became of the refreshed events' corrections by hand.
  */
import com.histmersion.api.catalogue.Correction
final case class EventSaved(created: EventSaved.Created, updated: EventSaved.Updated, rows: EventSaved.Rows, corrections: Correction.Counts = Correction.Counts())

object EventSaved:
  type Created = Created.T
  object Created extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Updated = Updated.T
  object Updated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Rows = Rows.T
  object Rows extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
