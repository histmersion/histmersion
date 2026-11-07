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
  def list(kind: Correction.TargetKind, id: Option[com.histmersion.domain.catalogue.Event.Id], status: Option[Correction.StatusValue], limit: com.histmersion.PageSize, offset: com.histmersion.Offset): Correction.List

object CorrectionService:
  def apply(db: Transactor): CorrectionService = Implementation(db)

  final private class Implementation(db: Transactor) extends CorrectionService:
    def list(kind: Correction.TargetKind, id: Option[com.histmersion.domain.catalogue.Event.Id], status: Option[Correction.StatusValue], limit: com.histmersion.PageSize, offset: com.histmersion.Offset): Correction.List =
      db.read(CorrectionRepo.list(kind, id.map(i => Correction.TargetId(i.value)), status, limit, offset))

  final class InMemory(state: AtomicReference[CatalogueState]) extends CorrectionService:
    def list(kind: Correction.TargetKind, id: Option[com.histmersion.domain.catalogue.Event.Id], status: Option[Correction.StatusValue], limit: com.histmersion.PageSize, offset: com.histmersion.Offset): Correction.List =
      val s = state.get
      val st = status.map(_.value.trim.toLowerCase).filter(_.nonEmpty)
      val one = id.map(_.value.trim).filter(_.nonEmpty)
      val all = s.corrections
        .filter(c =>
          c.targetKind == kind && one.forall(_ == c.targetId.value) && st
            .fold(c.status.value != Correction.Status.Agreed)(_ == c.status.value)
        )
        .sortBy(c => (c.status.value != Correction.Status.Conflict, c.targetId.value: String, c.field.value: String))
      val from = math.max(0, offset.value)
      val page = all.slice(from, from + math.max(1, limit.value)).toList.map { c =>
        val label = Option.when(c.targetKind.value == "event")(s.events.find(_.id.value == c.targetId.value).map(_.label)).flatten
        c.modify(_.label).setTo(label)
      }
      Correction.List(Correction.List.Total(all.size), page)
