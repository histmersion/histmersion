package com.histmersion.domain.catalogue.evidence

import com.histmersion.api.catalogue.{EventLinkCandidate, EventPassage}
import com.histmersion.util.database.Jsonb

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.database.given_DbCodec_List

/** The proposed links of events to sources (`event_link_candidates`) and when each event's were computed
  * (`event_link_checks`). Every method runs on the connection it is given.
  */
private[evidence] object EventLinkRepo:

  /** The stored candidates of one event, best first; `withRejected` includes the rejected ones. */
  type CandidatesId = CandidatesId.T
  object CandidatesId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CandidatesWithRejected = CandidatesWithRejected.T
  object CandidatesWithRejected extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SaveCandidatesId = SaveCandidatesId.T
  object SaveCandidatesId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetStatusResult = SetStatusResult.T
  object SetStatusResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetStatusId = SetStatusId.T
  object SetStatusId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetStatusStatus = SetStatusStatus.T
  object SetStatusStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsCheckedResult = IsCheckedResult.T
  object IsCheckedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsCheckedId = IsCheckedId.T
  object IsCheckedId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventsToCheckResult = EventsToCheckResult.T
  object EventsToCheckResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventsToCheckAll = EventsToCheckAll.T
  object EventsToCheckAll extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventsToCheckLimit = EventsToCheckLimit.T
  object EventsToCheckLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def candidates(id: com.histmersion.domain.catalogue.evidence.EventLinkRepo.CandidatesId, withRejected: com.histmersion.domain.catalogue.evidence.EventLinkRepo.CandidatesWithRejected = false)(using DbCon): List[EventLinkCandidate] =
    sql"""SELECT c.event_id, c.citekey, c.score, c.status, s.title, c.findings, c.passages::text, c.reasons
          FROM event_link_candidates c JOIN sources s ON s.citekey = c.citekey
          WHERE c.event_id = $id AND ($withRejected OR c.status <> 'rejected')
          ORDER BY c.status = 'confirmed' DESC, c.score DESC, c.citekey"""
      .query[CandidateRow]
      .run()
      .toList
      .map { r =>
        val ps = Jsonb.read[List[EventPassage]](r.passages).getOrElse(Nil)
        EventLinkCandidate(com.histmersion.api.catalogue.Event.Id(r.eventId), com.histmersion.api.library.Citekey(r.citekey), r.score, r.status, r.title, r.findings, ps, r.reasons)
      }

  /** Replace the PROPOSED candidates of one event with `cs`; a source confirmed or rejected before keeps its status
    * (its evidence is refreshed). Records the check.
    */
  def saveCandidates(id: com.histmersion.domain.catalogue.evidence.EventLinkRepo.SaveCandidatesId, cs: List[EventLinkCandidate])(using DbCon): Unit =
    sql"DELETE FROM event_link_candidates WHERE event_id = $id AND status = 'proposed'".update.run()
    cs.foreach { c =>
      val ps = Jsonb.write(c.passages)
      sql"""INSERT INTO event_link_candidates (event_id, citekey, score, findings, passages, reasons)
            VALUES ($id, ${c.citekey}, ${c.score}, ${c.findings}::text[], $ps::jsonb, ${c.reasons}::text[])
            ON CONFLICT (event_id, citekey) DO UPDATE SET score = EXCLUDED.score, findings = EXCLUDED.findings,
              passages = EXCLUDED.passages, reasons = EXCLUDED.reasons, computed_at = now()""".update.run()
    }
    sql"""INSERT INTO event_link_checks (event_id) VALUES ($id)
          ON CONFLICT (event_id) DO UPDATE SET checked_at = now()""".update.run()

  def setStatus(id: com.histmersion.domain.catalogue.evidence.EventLinkRepo.SetStatusId, citekey: com.histmersion.domain.library.Citekey, status: com.histmersion.domain.catalogue.evidence.EventLinkRepo.SetStatusStatus)(using DbCon): com.histmersion.domain.catalogue.evidence.EventLinkRepo.SetStatusResult =
    sql"UPDATE event_link_candidates SET status = $status WHERE event_id = $id AND citekey = $citekey".update
      .run() > 0

  def isChecked(id: com.histmersion.domain.catalogue.evidence.EventLinkRepo.IsCheckedId)(using DbCon): com.histmersion.domain.catalogue.evidence.EventLinkRepo.IsCheckedResult =
    sql"SELECT 1 FROM event_link_checks WHERE event_id = $id".query[Int].run().nonEmpty

  /** Events whose candidates were never computed (or every one, with `all`), catalogue ones first. */
  def eventsToCheck(all: com.histmersion.domain.catalogue.evidence.EventLinkRepo.EventsToCheckAll, limit: com.histmersion.domain.catalogue.evidence.EventLinkRepo.EventsToCheckLimit)(using DbCon): List[com.histmersion.domain.catalogue.evidence.EventLinkRepo.EventsToCheckResult] =
    sql"""SELECT e.id FROM events e
          WHERE ($all OR NOT EXISTS (SELECT 1 FROM event_link_checks k WHERE k.event_id = e.id))
          ORDER BY e.catalogue IS NULL, e.id LIMIT $limit""".query[String].run().toList
