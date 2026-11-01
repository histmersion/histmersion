package com.histmersion.domain.catalogue.evidence

import com.histmersion.api.EventLinkCandidate
import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.util.database.Transactor
import com.histmersion.values.EventLinkStatus
import hearth.kindlings.optics.syntax.*

import java.util.concurrent.atomic.AtomicReference

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
    def saveCandidates(id: String, cs: List[EventLinkCandidate]): Unit = db.transact(EventLinkRepo.saveCandidates(id, cs))
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
        .filter(c => c.eventId == id && (withRejected || c.status != EventLinkStatus.Rejected))
        .flatMap(c => title(c.citekey).map(t => c.modify(_.title).setTo(t)))
        .sortBy(c => (c.status != EventLinkStatus.Confirmed, -c.score, c.citekey))
        .toList

    def saveCandidates(id: String, cs: List[EventLinkCandidate]): Unit =
      val _ = state.updateAndGet { s =>
        val kept = s.candidates.filterNot(c => c.eventId == id && c.status == EventLinkStatus.Proposed)
        val written = cs.foldLeft(kept) { (acc, c) =>
          acc.find(o => o.eventId == id && o.citekey == c.citekey) match
            case Some(o) =>
              acc.map(x =>
                if x eq o then
                  x.modify(_.score)
                    .setTo(c.score)
                    .modify(_.findings)
                    .setTo(c.findings)
                    .modify(_.passages)
                    .setTo(c.passages)
                    .modify(_.reasons)
                    .setTo(c.reasons)
                else x
              )
            case None =>
              acc :+ c.modify(_.eventId).setTo(id).modify(_.status).setTo(EventLinkStatus.Proposed)
        }
        s.modify(_.candidates).setTo(written).modify(_.checked).using(_ + id)
      }

    def setStatus(id: String, citekey: String, status: String): Boolean =
      val had = state.get.candidates.exists(c => c.eventId == id && c.citekey == citekey)
      val _ = state.updateAndGet(
        _.modify(_.candidates)
          .using(_.map(c => if c.eventId == id && c.citekey == citekey then c.modify(_.status).setTo(status) else c))
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
