package com.histmersion.domain.catalogue.events

import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.util.database.Transactor
import hearth.kindlings.optics.syntax.*

import java.util.concurrent.atomic.AtomicReference
import com.histmersion.api.catalogue.Correction

/** The corrections by hand of what an importer wrote: a person's value for one field, kept over the catalogue's. */
trait CorrectionService:
  /** The corrections of `kind` (one target's with `id`), those in `status` (default: all but the retired), conflicts
    * first, with each event's label.
    */
  def list(kind: String, id: Option[String], status: Option[String], limit: Int, offset: Int): Correction.List

object CorrectionService:
  def apply(db: Transactor): CorrectionService = Implementation(db)

  final private class Implementation(db: Transactor) extends CorrectionService:
    def list(kind: String, id: Option[String], status: Option[String], limit: Int, offset: Int): Correction.List =
      db.read(CorrectionRepo.list(kind, id, status, limit, offset))

  final class InMemory(state: AtomicReference[CatalogueState]) extends CorrectionService:
    def list(kind: String, id: Option[String], status: Option[String], limit: Int, offset: Int): Correction.List =
      val s = state.get
      val st = status.map(_.trim.toLowerCase).filter(_.nonEmpty)
      val one = id.map(_.trim).filter(_.nonEmpty)
      val all = s.corrections
        .filter(c =>
          c.targetKind == kind && one.forall(_ == c.targetId) && st
            .fold(c.status != Correction.Status.Agreed)(_ == c.status)
        )
        .sortBy(c => (c.status != Correction.Status.Conflict, c.targetId, c.field))
      val from = offset.max(0)
      val page = all.slice(from, from + limit.max(1)).toList.map { c =>
        val label = Option.when(c.targetKind == "event")(s.events.find(_.id == c.targetId).map(_.label)).flatten
        c.modify(_.label).setTo(label)
      }
      Correction.List(all.size, page)
