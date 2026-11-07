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

  def candidates(id: com.histmersion.domain.catalogue.events.BEventRow.Id, withRejected: Boolean = false)(using DbCon): List[EventLinkCandidate] =
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
  def saveCandidates(id: com.histmersion.domain.catalogue.events.BEventRow.Id, cs: List[EventLinkCandidate])(using DbCon): Unit =
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

  def setStatus(id: com.histmersion.domain.catalogue.events.BEventRow.Id, citekey: com.histmersion.domain.library.Citekey, status: com.histmersion.domain.catalogue.evidence.BCandidateRow.Status)(using DbCon): Boolean =
    sql"UPDATE event_link_candidates SET status = $status WHERE event_id = $id AND citekey = $citekey".update
      .run() > 0

  def isChecked(id: com.histmersion.domain.catalogue.events.BEventRow.Id)(using DbCon): Boolean =
    sql"SELECT 1 FROM event_link_checks WHERE event_id = $id".query[Int].run().nonEmpty

  /** Events whose candidates were never computed (or every one, with `all`), catalogue ones first. */
  def eventsToCheck(all: Boolean, limit: com.histmersion.PageSize)(using DbCon): List[com.histmersion.domain.catalogue.events.BEventRow.Id] =
    sql"""SELECT e.id FROM events e
          WHERE ($all OR NOT EXISTS (SELECT 1 FROM event_link_checks k WHERE k.event_id = e.id))
          ORDER BY e.catalogue IS NULL, e.id LIMIT $limit""".query[String].run().toList
