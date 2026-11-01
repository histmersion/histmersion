package com.histmersion.domain.catalogue.evidence

import com.histmersion.api.*
import com.histmersion.db.{BundleImport, Sql}
import com.histmersion.util.data.exchange.{BundleFormat, BundleScope}
import com.histmersion.util.data.exchange.BundleSql.*

import com.histmersion.db.given
import com.augustnagro.magnum.*
import java.util.UUID

/** The evidence as a bundle carries it (docs/research-merge-import.md §4, §7.3): the attestations, the sources that
  * possibly discuss an event and when they were last computed — read out in a deterministic order, and written in by an
  * import.
  */
private[evidence] object EvidenceBundleRepo:

  // ── out ──────────────────────────────────────────────────────────────────────────────────────

  def attestations(scope: BundleScope)(using DbCon): Vector[BundleAttestation] =
    (Sql.raw(s"""SELECT t.uid, t.origin, s.uid, t.target_type, t.target_id,
                       ${targetUid("t.target_type", "t.target_id")}, t.quote, t.prefix, t.suffix,
                       t.char_start, t.char_end, t.chunk_ord, t.page, t.canvas, t.xywh, t.cts_urn, t.note,
                       ${ts("t.created_at")}, ${ts("t.updated_at")}
                FROM attestations t JOIN sources s ON s.citekey = t.citekey WHERE """) ++
      where(scope, "t.uid", "t.updated_at") ++ " ORDER BY t.uid").frag
      .query[BAttestationFullRow]
      .run()
      .map(r =>
        BundleAttestation(
          r.uid,
          r.origin,
          r.sourceUid,
          r.targetType,
          r.targetId,
          r.targetUid,
          r.quote,
          r.prefix,
          r.suffix,
          r.charStart,
          r.charEnd,
          r.chunkOrd,
          r.page,
          r.canvas,
          r.xywh,
          r.ctsUrn,
          r.note,
          r.createdAt,
          r.updatedAt
        )
      )

  /** The sources that possibly discuss the events in scope (`event_link_candidates`). An incremental bundle carries
    * those recomputed after `since` and every DECISION (a status change does not move `computed_at`, and the decisions
    * are few).
    */
  def eventCandidates(scope: BundleScope)(using DbCon): Vector[BundleEventCandidate] =
    val w = scope match
      case BundleScope.Since(_) =>
        Sql.raw("(c.status <> 'proposed' OR ") ++ where(scope, "e.uid", "c.computed_at") ++ ")"
      case _ => where(scope, "e.uid", "c.computed_at")
    (Sql.raw(s"""SELECT e.uid, s.uid, c.citekey, c.score, c.status, c.findings, c.passages::text, c.reasons,
                       ${ts("c.computed_at")}
                FROM event_link_candidates c JOIN events e ON e.id = c.event_id
                JOIN sources s ON s.citekey = c.citekey WHERE """) ++ w ++ " ORDER BY e.uid, s.uid").frag
      .query[BCandidateRow]
      .run()
      .map { r =>
        BundleEventCandidate(
          r.eventUid,
          r.sourceUid,
          r.citekey,
          r.score,
          r.status,
          r.findings,
          io.circe.parser.parse(r.passages).getOrElse(io.circe.Json.arr()),
          r.reasons,
          r.computedAt
        )
      }

  /** When the candidates of the events in scope were last computed (`event_link_checks`). */
  def eventChecks(scope: BundleScope)(using DbCon): Vector[BundleEventCheck] =
    (Sql.raw(s"""SELECT e.uid, ${ts("k.checked_at")}
                FROM event_link_checks k JOIN events e ON e.id = k.event_id WHERE """) ++
      where(scope, "e.uid", "k.checked_at") ++ " ORDER BY e.uid").frag
      .query[(UUID, String)]
      .run()
      .map(BundleEventCheck.apply)

  // ── in ───────────────────────────────────────────────────────────────────────────────────────

  /** Each event of the candidates and checks looked up once, in one step. */
  private def eventIdsOf()(using DbCon): UUID => Option[String] =
    val ids = scala.collection.mutable.Map.empty[UUID, Option[String]]
    u => ids.getOrElseUpdate(u, sql"SELECT id FROM events WHERE uid = $u".query[String].run().headOption)

  /** The candidates: the newer evidence, a decision kept over a proposal (BundleEventSets). */
  def candidatesIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    val eventIds = eventIdsOf()
    import BundleEventSets.newer
    if w.es.eventCandidates.nonEmpty then
      val here = Sql
        .raw(s"SELECT event_id, citekey, status, ${ts("computed_at")} FROM event_link_candidates")
        .frag
        .query[(String, String, String, String)]
        .run()
        .map((e, c, st, at) => (e, c) -> (st, at))
        .toMap
      w.es.eventCandidates.foreach { c =>
        for id <- eventIds(c.eventUid); ck <- w.citekeyHere(c.sourceUid) do
          val passages = BundleFormat.printer.print(c.passages)
          here.get((id, ck)) match
            case None =>
              sql"""INSERT INTO event_link_candidates (event_id, citekey, score, status, findings, passages, reasons, computed_at)
                    VALUES ($id, $ck, ${c.score}, ${c.status}, ${c.findings}::text[], $passages::jsonb,
                      ${c.reasons}::text[], ${c.computedAt}::timestamptz)""".update.run(): Unit
            case Some((status, at)) =>
              val keep = BundleEventSets.candidateStatus(status, c.status)
              if newer(Some(at), Some(c.computedAt)) then
                sql"""UPDATE event_link_candidates SET score = ${c.score}, status = $keep, findings = ${c.findings}::text[],
                        passages = $passages::jsonb, reasons = ${c.reasons}::text[], computed_at = ${c.computedAt}::timestamptz
                      WHERE event_id = $id AND citekey = $ck""".update.run(): Unit
              else if keep != status then
                sql"UPDATE event_link_candidates SET status = $keep WHERE event_id = $id AND citekey = $ck".update
                  .run(): Unit
      }

  /** When the candidates were last computed: the newer check. */
  def checksIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    val eventIds = eventIdsOf()
    import BundleEventSets.newer
    if w.es.eventChecks.nonEmpty then
      val here = Sql
        .raw(s"SELECT event_id, ${ts("checked_at")} FROM event_link_checks")
        .frag
        .query[(String, String)]
        .run()
        .toMap
      w.es.eventChecks.foreach { k =>
        eventIds(k.eventUid).filter(id => newer(here.get(id), Some(k.checkedAt))).foreach { id =>
          sql"""INSERT INTO event_link_checks (event_id, checked_at) VALUES ($id, ${k.checkedAt}::timestamptz)
                ON CONFLICT (event_id) DO UPDATE SET checked_at = EXCLUDED.checked_at""".update.run()
        }
      }

  /** This collection's own attestations a merge moved into the body it kept, or dropped: written as this collection's
    * own rows, so their clocks move and a drop leaves a tombstone.
    */
  def attestationFixes(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    if w.merged.attestations.nonEmpty then
      w.asLocal {
        w.merged.attestations.foreach { f =>
          if f.drop then sql"DELETE FROM attestations WHERE uid = ${f.uid}".update.run()
          else sql"""UPDATE attestations SET char_start = ${f.anchor.map(_.start)}, char_end = ${f.anchor.map(_.end)},
                    chunk_ord = ${f.chunkOrd} WHERE uid = ${f.uid}""".update.run()
        }
      }

  /** The attestations, once their sources and what they attest are in: a target by its label here. */
  def attestationsIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    w.es.attestations.filter(t => w.writes(t.uid)).foreach { t =>
      val ck = w.citekeyOf(t.sourceUid)
      val target = w.targetLabel(t.targetType, t.targetId, t.targetUid)
      sql"""INSERT INTO attestations (uid, origin, citekey, target_type, target_id, quote, prefix, suffix,
              char_start, char_end, chunk_ord, page, canvas, xywh, cts_urn, note, created_at, updated_at)
            VALUES (${t.uid}, ${t.origin}, $ck, ${t.targetType}, $target, ${t.quote}, ${t.prefix}, ${t.suffix},
              ${t.charStart}, ${t.charEnd}, ${t.chunkOrd}, ${t.page}, ${t.canvas}, ${t.xywh}, ${t.ctsUrn},
              ${t.note}, ${t.createdAt}::timestamptz, ${t.updatedAt}::timestamptz)
            ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, citekey = EXCLUDED.citekey,
              target_type = EXCLUDED.target_type, target_id = EXCLUDED.target_id, quote = EXCLUDED.quote,
              prefix = EXCLUDED.prefix, suffix = EXCLUDED.suffix, char_start = EXCLUDED.char_start,
              char_end = EXCLUDED.char_end, chunk_ord = EXCLUDED.chunk_ord, page = EXCLUDED.page,
              canvas = EXCLUDED.canvas, xywh = EXCLUDED.xywh, cts_urn = EXCLUDED.cts_urn,
              note = EXCLUDED.note, created_at = EXCLUDED.created_at,
              updated_at = EXCLUDED.updated_at""".update.run()
    }
