package com.histmersion
package domain.catalogue.evidence

import com.histmersion.api.catalogue.EventLinkCandidate
import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.util.database.Transactor
import hearth.kindlings.optics.syntax.*

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import com.histmersion.domain.catalogue.Event
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.catalogue.events.BEventRow
import com.histmersion.domain.Title
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator

/** The sources proposed as discussing an event — scored, then confirmed or rejected — and which events' proposals were
  * computed.
  */
trait EventLinkService:
  /** One event's candidates, the confirmed first, then by score; the rejected ones only when `rejected` lists them. A
    * candidate whose source is gone is not listed.
    */
  def candidates(id: Event.Id, rejected: RejectedLinks = RejectedLinks.Hidden): List[EventLinkCandidate]

  /** Replace the event's proposed candidates with `cs` (one confirmed or rejected before keeps its status) and record
    * the check.
    */
  def saveCandidates(id: Event.Id, cs: List[EventLinkCandidate])(using WritePermit)(using Creator.New): Unit

  /** Set one candidate's status; `false` when the event has no candidate of that source. */
  def setStatus(id: Event.Id, citekey: Citekey, status: Event.LinkStatus.Wire)(using WritePermit): Boolean

  def isChecked(id: Event.Id): Boolean

  /** The events whose candidates were never computed (or every one), the catalogue's first, by id. */
  def eventsToCheck(which: EventsToCheck, limit: PageSize): List[Event.Id]

object EventLinkService:

  def apply(db: Transactor): EventLinkService = Implementation(db)

  final private class Implementation(db: Transactor) extends EventLinkService:
    def candidates(id: Event.Id, rejected: RejectedLinks): List[EventLinkCandidate] =
      db.read(EventLinkRepo.candidates(BEventRow.Id(id.value), rejected))
    def saveCandidates(id: Event.Id, cs: List[EventLinkCandidate])(using WritePermit)(using Creator.New): Unit =
      db.write(EventLinkRepo.saveCandidates(BEventRow.Id(id.value), cs))
    def setStatus(id: Event.Id, citekey: Citekey, status: Event.LinkStatus.Wire)(using WritePermit): Boolean =
      db.write(EventLinkRepo.setStatus(BEventRow.Id(id.value), citekey, BCandidateRow.Status(status.value)))
    def isChecked(id: Event.Id): Boolean = db.read(EventLinkRepo.isChecked(BEventRow.Id(id.value)))
    def eventsToCheck(which: EventsToCheck, limit: PageSize): List[Event.Id] =
      val all = which == EventsToCheck.Every
      db.read(EventLinkRepo.eventsToCheck(all = all, limit)).map(i => Event.Id.assume(i.value))

  /** The same rules over the shared in-memory state; `title` names a source by its citekey (the library's), `None` for
    * one that is gone.
    */
  final class InMemory(state: AtomicReference[CatalogueState], title: Citekey => Option[Option[Title]])
      extends EventLinkService:
    def candidates(id: Event.Id, rejected: RejectedLinks): List[EventLinkCandidate] =
      val apiId = com.histmersion.api.catalogue.Event.Id(id.value)
      state.get.candidates
        .filter(c =>
          c.eventId == apiId && (rejected == RejectedLinks.Listed || c.status.value != Event.LinkStatus.Rejected)
        )
        .flatMap(c =>
          Citekey
            .option(c.citekey.value)
            .flatMap(title)
            .map(t => c.modify(_.title).setTo(t.map(x => com.histmersion.api.library.Title(x.value))))
        )
        .sortBy(c => (c.status.value != Event.LinkStatus.Confirmed, -c.score.value, c.citekey.value.toString))
        .toList

    def saveCandidates(id: Event.Id, cs: List[EventLinkCandidate])(using WritePermit)(using Creator.New): Unit =
      val _ = state.updateAndGet { s =>
        val apiId = com.histmersion.api.catalogue.Event.Id(id.value)
        val kept = s.candidates.filterNot(c => c.eventId == apiId && c.status.value == Event.LinkStatus.Proposed)
        @tailrec def loop(rest: List[EventLinkCandidate], acc: Vector[EventLinkCandidate]): Vector[EventLinkCandidate] =
          rest match
            case c :: tail =>
              val decided = acc.indexWhere(o => o.eventId == apiId && o.citekey == c.citekey)
              val next =
                if decided >= 0 then
                  acc.updated(
                    decided,
                    acc(decided)
                      .modify(_.score)
                      .setTo(c.score)
                      .modify(_.findings)
                      .setTo(c.findings)
                      .modify(_.passages)
                      .setTo(c.passages)
                      .modify(_.reasons)
                      .setTo(c.reasons)
                  )
                else
                  acc :+ c
                    .modify(_.eventId)
                    .setTo(apiId)
                    .modify(_.status)
                    .setTo(EventLinkCandidate.StatusValue(Event.LinkStatus.Proposed))
              loop(tail, next)
            case Nil => acc
        val written = loop(cs, kept)
        s.modify(_.candidates).setTo(written).modify(_.checked).using(_ + CatalogueState.Checked(id.value))
      }

    def setStatus(id: Event.Id, citekey: Citekey, status: Event.LinkStatus.Wire)(using WritePermit): Boolean =
      val apiId = com.histmersion.api.catalogue.Event.Id(id.value)
      val apiKey = com.histmersion.api.library.Citekey(citekey.value)
      val had = state.get.candidates.exists(c => c.eventId == apiId && c.citekey == apiKey)
      val _ = state.updateAndGet(
        _.modify(_.candidates)
          .using(
            _.map(c =>
              if c.eventId == apiId && c.citekey == apiKey then
                c.modify(_.status).setTo(EventLinkCandidate.StatusValue(status.value))
              else c
            )
          )
      )
      had

    def isChecked(id: Event.Id): Boolean = state.get.checked(CatalogueState.Checked(id.value))

    def eventsToCheck(which: EventsToCheck, limit: PageSize): List[Event.Id] =
      val s = state.get
      s.events
        .filter(e => which == EventsToCheck.Every || !s.checked(CatalogueState.Checked(e.id.value)))
        .sortBy(e => (e.catalogue.isEmpty, e.id.value))
        .take(limit.value.max(0))
        .map(_.id)
        .toList
