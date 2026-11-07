package com.histmersion.domain.catalogue.evidence

import com.histmersion.api.catalogue.EventLinkCandidate
import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.util.database.Transactor
import hearth.kindlings.optics.syntax.*

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import com.histmersion.domain.catalogue.Event

/** The sources proposed as discussing an event — scored, then confirmed or rejected — and which events' proposals were
  * computed.
  */
trait EventLinkService:
  /** One event's candidates, the confirmed first, then by score; `withRejected` includes the rejected ones. A candidate
    * whose source is gone is not listed.
    */
  def candidates(id: com.histmersion.domain.catalogue.Event.Id, withRejected: Boolean = false): List[EventLinkCandidate]

  /** Replace the event's proposed candidates with `cs` (one confirmed or rejected before keeps its status) and record
    * the check.
    */
  def saveCandidates(id: com.histmersion.domain.catalogue.Event.Id, cs: List[EventLinkCandidate]): Unit

  /** Set one candidate's status; `false` when the event has no candidate of that source. */
  def setStatus(id: com.histmersion.domain.catalogue.Event.Id, citekey: com.histmersion.domain.library.Citekey, status: com.histmersion.domain.catalogue.Event.LinkStatus.Wire): Boolean

  def isChecked(id: com.histmersion.domain.catalogue.Event.Id): Boolean

  /** The events whose candidates were never computed (every one, with `all`), the catalogue's first, by id. */
  def eventsToCheck(all: Boolean, limit: com.histmersion.PageSize): List[com.histmersion.domain.catalogue.Event.Id]

object EventLinkService:

  def apply(db: Transactor): EventLinkService = Implementation(db)

  final private class Implementation(db: Transactor) extends EventLinkService:
    def candidates(id: com.histmersion.domain.catalogue.Event.Id, withRejected: Boolean): List[EventLinkCandidate] =
      db.read(EventLinkRepo.candidates(com.histmersion.domain.catalogue.events.BEventRow.Id(id.value), withRejected))
    def saveCandidates(id: com.histmersion.domain.catalogue.Event.Id, cs: List[EventLinkCandidate]): Unit =
      db.transact(EventLinkRepo.saveCandidates(com.histmersion.domain.catalogue.events.BEventRow.Id(id.value), cs))
    def setStatus(id: com.histmersion.domain.catalogue.Event.Id, citekey: com.histmersion.domain.library.Citekey, status: com.histmersion.domain.catalogue.Event.LinkStatus.Wire): Boolean =
      db.transact(EventLinkRepo.setStatus(com.histmersion.domain.catalogue.events.BEventRow.Id(id.value), citekey, BCandidateRow.Status(status.value)))
    def isChecked(id: com.histmersion.domain.catalogue.Event.Id): Boolean = db.read(EventLinkRepo.isChecked(com.histmersion.domain.catalogue.events.BEventRow.Id(id.value)))
    def eventsToCheck(all: Boolean, limit: com.histmersion.PageSize): List[com.histmersion.domain.catalogue.Event.Id] = db.read(EventLinkRepo.eventsToCheck(all, limit)).map(i => com.histmersion.domain.catalogue.Event.Id.assume(i.value))


  /** The same rules over the shared in-memory state; `title` names a source by its citekey (the library's), `None` for
    * one that is gone.
    */
  final class InMemory(state: AtomicReference[CatalogueState], title: com.histmersion.domain.library.Citekey => Option[Option[com.histmersion.domain.Title]])
      extends EventLinkService:
    def candidates(id: com.histmersion.domain.catalogue.Event.Id, withRejected: Boolean): List[EventLinkCandidate] =
      val apiId = com.histmersion.api.catalogue.Event.Id(id.value)
      state.get.candidates
        .filter(c => c.eventId == apiId && (withRejected || c.status.value != Event.LinkStatus.Rejected))
        .flatMap(c =>
          com.histmersion.domain.library.Citekey
            .option(c.citekey.value)
            .flatMap(title)
            .map(t => c.modify(_.title).setTo(t.map(x => com.histmersion.api.library.Title(x.value))))
        )
        .sortBy(c => (c.status.value != Event.LinkStatus.Confirmed, -c.score.value, c.citekey.value.toString))
        .toList

    def saveCandidates(id: com.histmersion.domain.catalogue.Event.Id, cs: List[EventLinkCandidate]): Unit =
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

    def setStatus(id: com.histmersion.domain.catalogue.Event.Id, citekey: com.histmersion.domain.library.Citekey, status: com.histmersion.domain.catalogue.Event.LinkStatus.Wire): Boolean =
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

    def isChecked(id: com.histmersion.domain.catalogue.Event.Id): Boolean = state.get.checked(CatalogueState.Checked(id.value))

    def eventsToCheck(all: Boolean, limit: com.histmersion.PageSize): List[com.histmersion.domain.catalogue.Event.Id] =
      val s = state.get
      s.events
        .filter(e => all || !s.checked(CatalogueState.Checked(e.id.value)))
        .sortBy(e => (e.catalogue.isEmpty, e.id.value))
        .take(limit.value.max(0))
        .map(_.id)
        .toList
