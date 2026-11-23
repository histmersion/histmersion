package com.histmersion
package domain.catalogue.evidence
import com.histmersion.api.json.NewtypeCodecs.given

import com.histmersion.api.catalogue.{EventLinkCandidate, EventPassage}
import com.histmersion.util.database.Jsonb

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.database.given_DbCodec_List
import com.histmersion.domain.catalogue.events.BEventRow
import com.histmersion.api.catalogue.Event
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Title
import com.histmersion.api.library.Finding
import com.histmersion.util.database.Unused

/** The proposed links of events to sources (`event_link_candidates`) and when each event's were computed
  * (`event_link_checks`). Every method runs on the connection it is given.
  */
private[evidence] object EventLinkRepo:

  /** The stored candidates of one event, best first; the rejected ones only when `rejected` lists them. */

  def candidates(id: BEventRow.Id, rejected: RejectedLinks = RejectedLinks.Hidden)(using
      DbCon
  ): List[EventLinkCandidate] =
    val listed = rejected == RejectedLinks.Listed
    sql"""SELECT c.event_id, c.citekey, c.score, c.status, s.title, c.findings, c.passages::text, c.reasons
          FROM event_link_candidates c JOIN sources s ON s.citekey = c.citekey
          WHERE c.event_id = $id AND ($listed OR c.status <> 'rejected')
          ORDER BY c.status = 'confirmed' DESC, c.score DESC, c.citekey"""
      .query[CandidateRow]
      .run()
      .toList
      .map { r =>
        val ps = Jsonb.read[List[EventPassage]](Jsonb.JsonText(r.passages.value)).getOrElse(Nil)
        EventLinkCandidate(
          Event.Id(r.eventId.value),
          Citekey(r.citekey.value),
          EventLinkCandidate.Score(r.score.value),
          EventLinkCandidate.StatusValue(r.status.value),
          r.title.map(t => Title(t.value)),
          r.findings.map(f => Finding.Id(f.value)),
          ps,
          r.reasons.map(x => EventLinkCandidate.Reasons(x.value))
        )
      }

  /** Replace the PROPOSED candidates of one event with `cs`; a source confirmed or rejected before keeps its status
    * (its evidence is refreshed). Records the check.
    */
  def saveCandidates(id: BEventRow.Id, cs: List[EventLinkCandidate])(using DbCon): Unit =
    Unused(
      sql"DELETE FROM event_link_candidates WHERE event_id = $id AND status = 'proposed'".update.run(),
      Unused.Why.Idempotent
    )
    cs.foreach { c =>
      val ps = Jsonb.write(c.passages)
      sql"""INSERT INTO event_link_candidates (event_id, citekey, score, findings, passages, reasons)
            VALUES ($id, ${c.citekey}, ${c.score}, ${c.findings}::text[], $ps::jsonb, ${c.reasons}::text[])
            ON CONFLICT (event_id, citekey) DO UPDATE SET score = EXCLUDED.score, findings = EXCLUDED.findings,
              passages = EXCLUDED.passages, reasons = EXCLUDED.reasons, computed_at = now()""".update.run()
    }
    sql"""INSERT INTO event_link_checks (event_id) VALUES ($id)
          ON CONFLICT (event_id) DO UPDATE SET checked_at = now()""".update.run()

  def setStatus(id: BEventRow.Id, citekey: domain.library.Citekey, status: BCandidateRow.Status)(using
      DbCon
  ): Boolean =
    sql"UPDATE event_link_candidates SET status = $status WHERE event_id = $id AND citekey = $citekey".update
      .run() > 0

  def isChecked(id: BEventRow.Id)(using DbCon): Boolean =
    sql"SELECT 1 FROM event_link_checks WHERE event_id = $id".query[Int].run().nonEmpty

  /** Events whose candidates were never computed (or every one, with `all`), catalogue ones first. */
  def eventsToCheck(all: Boolean, limit: PageSize)(using DbCon): List[BEventRow.Id] =
    sql"""SELECT e.id FROM events e
          WHERE ($all OR NOT EXISTS (SELECT 1 FROM event_link_checks k WHERE k.event_id = e.id))
          ORDER BY e.catalogue IS NULL, e.id LIMIT $limit""".query[BEventRow.Id].run().toList
