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
  def candidates(id: String, withRejected: Boolean = false): List[EventLinkCandidate]

  /** Replace the event's proposed candidates with `cs` (one confirmed or rejected before keeps its status) and record
    * the check.
    */
  def saveCandidates(id: String, cs: List[EventLinkCandidate]): Unit

  /** Set one candidate's status; `false` when the event has no candidate of that source. */
  def setStatus(id: String, citekey: String, status: String): Boolean

  def isChecked(id: String): Boolean

  /** The events whose candidates were never computed (every one, with `all`), the catalogue's first, by id. */
  def eventsToCheck(all: Boolean, limit: Int): List[String]

object EventLinkService:
  def apply(db: Transactor): EventLinkService = Implementation(db)

  final private class Implementation(db: Transactor) extends EventLinkService:
    def candidates(id: String, withRejected: Boolean): List[EventLinkCandidate] =
      db.read(EventLinkRepo.candidates(id, withRejected))
    def saveCandidates(id: String, cs: List[EventLinkCandidate]): Unit =
      db.transact(EventLinkRepo.saveCandidates(id, cs))
    def setStatus(id: String, citekey: String, status: String): Boolean =
      db.transact(EventLinkRepo.setStatus(id, citekey, status))
    def isChecked(id: String): Boolean = db.read(EventLinkRepo.isChecked(id))
    def eventsToCheck(all: Boolean, limit: Int): List[String] = db.read(EventLinkRepo.eventsToCheck(all, limit))

  /** The same rules over the shared in-memory state; `title` names a source by its citekey (the library's), `None` for
    * one that is gone.
    */
  final class InMemory(state: AtomicReference[CatalogueState], title: String => Option[Option[String]])
      extends EventLinkService:
    def candidates(id: String, withRejected: Boolean): List[EventLinkCandidate] =
      state.get.candidates
        .filter(c => c.eventId == com.histmersion.api.catalogue.Event.Id(id) && (withRejected || c.status != Event.LinkStatus.Rejected))
        .flatMap(c => title(c.citekey.value).map(t => c.modify(_.title).setTo(t)))
        .sortBy(c => (c.status != Event.LinkStatus.Confirmed, -c.score, c.citekey.value.toString))
        .toList

    def saveCandidates(id: String, cs: List[EventLinkCandidate]): Unit =
      val _ = state.updateAndGet { s =>
        val kept = s.candidates.filterNot(c => c.eventId == com.histmersion.api.catalogue.Event.Id(id) && c.status == Event.LinkStatus.Proposed)
        @tailrec def loop(rest: List[EventLinkCandidate], acc: Vector[EventLinkCandidate]): Vector[EventLinkCandidate] =
          rest match
            case c :: tail =>
              val decided = acc.indexWhere(o => o.eventId == com.histmersion.api.catalogue.Event.Id(id) && o.citekey == c.citekey)
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
                else acc :+ c.modify(_.eventId).setTo(com.histmersion.api.catalogue.Event.Id(id)).modify(_.status).setTo(Event.LinkStatus.Proposed)
              loop(tail, next)
            case Nil => acc
        val written = loop(cs, kept)
        s.modify(_.candidates).setTo(written).modify(_.checked).using(_ + id)
      }

    def setStatus(id: String, citekey: String, status: String): Boolean =
      val had = state.get.candidates.exists(c => c.eventId == com.histmersion.api.catalogue.Event.Id(id) && c.citekey == com.histmersion.api.library.Citekey(citekey))
      val _ = state.updateAndGet(
        _.modify(_.candidates)
          .using(_.map(c => if c.eventId == com.histmersion.api.catalogue.Event.Id(id) && c.citekey == com.histmersion.api.library.Citekey(citekey) then c.modify(_.status).setTo(status) else c))
      )
      had

    def isChecked(id: String): Boolean = state.get.checked(id)

    def eventsToCheck(all: Boolean, limit: Int): List[String] =
      val s = state.get
      s.events
        .filter(e => all || !s.checked(e.id))
        .sortBy(e => (e.catalogue.isEmpty, e.id))
        .take(limit.max(0))
        .map(_.id)
        .toList
