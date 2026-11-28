package com.histmersion
package domain.catalogue.evidence
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.json.RawJson

import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.data.exchange.{BundleFormat, BundleScope}
import com.histmersion.util.data.exchange.BundleSql.*

import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.augustnagro.magnum.*
import java.util.UUID
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.library.Citekey
import com.histmersion.domain.catalogue.events.BEventRow
import com.histmersion.util.database.Timestamps
import com.histmersion.util.database.Unused
import com.histmersion.domain.Creator
import com.histmersion.util.database.StoredCreator
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The evidence as a bundle carries it (docs/research-merge-import.md §4, §7.3): the attestations, the sources that
  * possibly discuss an event and when they were last computed — read out in a deterministic order, and written in by an
  * import.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@atBoundary(BoundaryKind.Sql, "reads and writes the evidence rows for bundle export and import")
private[evidence] object EvidenceBundleRepo:

  // ── out ──────────────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def attestations(scope: BundleScope)(using DbCon): Vector[Bundle.Attestation] =
    sql"""SELECT t.uid, t.origin, s.uid, t.target_type, t.target_id,
                       ${targetUid(sql"t.target_type", sql"t.target_id")}, t.quote, t.prefix, t.suffix,
                       t.char_start, t.char_end, t.chunk_ord, t.page, t.canvas, t.xywh, t.cts_urn, t.note,
                       ${Timestamps.iso(sql"t.created_at")}, ${Timestamps.iso(sql"t.updated_at")}, t.creator::text
                FROM attestations t JOIN sources s ON s.citekey = t.citekey WHERE ${where(
        scope,
        sql"t.uid",
        sql"t.updated_at"
      )} ORDER BY t.uid"""
      .query[BAttestationFullRow]
      .run()
      .map(r =>
        Bundle.Attestation(
          r.uid,
          r.origin,
          r.sourceUid,
          Bundle.Attestation.TargetType(r.targetType.value),
          Bundle.Attestation.TargetId(r.targetId.value),
          r.targetUid,
          r.quote.map(v => Bundle.Attestation.Quote(v.value)),
          r.prefix.map(v => Bundle.Attestation.Prefix(v.value)),
          r.suffix.map(v => Bundle.Attestation.Suffix(v.value)),
          r.charStart.map(v => Bundle.Attestation.CharStart(v.value)),
          r.charEnd.map(v => Bundle.Attestation.CharEnd(v.value)),
          r.chunkOrd.map(v => Bundle.Attestation.ChunkOrd(v.value)),
          r.page.map(v => Bundle.Attestation.Page(v.value)),
          r.canvas.map(v => Bundle.Attestation.Canvas(v.value)),
          r.xywh.map(v => Bundle.Attestation.Xywh(v.value)),
          r.ctsUrn.map(v => Bundle.Attestation.CtsUrn(v.value)),
          r.note.map(v => Bundle.Attestation.Note(v.value)),
          Bundle.Attestation.CreatedAt(r.createdAt.value),
          Bundle.Attestation.UpdatedAt(r.updatedAt.value),
          creator = StoredCreator.document(r.creator)
        )
      )

  /** The sources that possibly discuss the events in scope (`event_link_candidates`). An incremental bundle carries
    * those recomputed after `since` and every DECISION (a status change does not move `computed_at`, and the decisions
    * are few).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def eventCandidates(scope: BundleScope)(using DbCon): Vector[Bundle.EventCandidate] =
    val w = scope match
      case BundleScope.Since(_) =>
        sql"(c.status <> 'proposed' OR ${where(scope, sql"e.uid", sql"c.computed_at")})"
      case _ => where(scope, sql"e.uid", sql"c.computed_at")
    sql"""SELECT e.uid, s.uid, c.citekey, c.score, c.status, c.findings, c.passages::text, c.reasons,
                       ${Timestamps.iso(sql"c.computed_at")}
                FROM event_link_candidates c JOIN events e ON e.id = c.event_id
                JOIN sources s ON s.citekey = c.citekey WHERE $w ORDER BY e.uid, s.uid"""
      .query[BCandidateRow]
      .run()
      .map { r =>
        Bundle.EventCandidate(
          r.eventUid,
          r.sourceUid,
          Citekey(r.citekey.value),
          Bundle.EventCandidate.Score(r.score.value),
          Bundle.EventCandidate.Status(r.status.value),
          r.findings.map(f => Bundle.EventCandidate.Findings(f.value)),
          RawJson.parse(r.passages.value).getOrElse(Json.arr()),
          r.reasons.map(x => Bundle.EventCandidate.Reasons(x.value)),
          Bundle.EventCandidate.ComputedAt(r.computedAt.value)
        )
      }

  /** When the candidates of the events in scope were last computed (`event_link_checks`). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def eventChecks(scope: BundleScope)(using DbCon): Vector[Bundle.EventCheck] =
    sql"""SELECT e.uid, ${Timestamps.iso(sql"k.checked_at")}
                FROM event_link_checks k JOIN events e ON e.id = k.event_id WHERE ${where(
        scope,
        sql"e.uid",
        sql"k.checked_at"
      )} ORDER BY e.uid"""
      .query[(UUID, String)]
      .run()
      .map((uid, at) => Bundle.EventCheck(uid, Bundle.EventCheck.CheckedAt(at)))

  // ── in ───────────────────────────────────────────────────────────────────────────────────────

  /** Each event of the candidates and checks looked up once, in one step. */
  private def eventIdsOf()(using DbCon): UUID => Option[BEventRow.Id] =
    val ids = scala.collection.mutable.Map.empty[UUID, Option[BEventRow.Id]]
    u =>
      ids.getOrElseUpdate(
        u,
        sql"SELECT id FROM events WHERE uid = $u".query[BEventRow.Id].run().headOption
      )

  /** The candidates: the newer evidence, a decision kept over a proposal (BundleEventSets). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def candidatesIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    val eventIds = eventIdsOf()
    import com.histmersion.api.exchange.Bundle.EventSets.newer
    if w.es.eventCandidates.nonEmpty then
      val here = sql"SELECT event_id, citekey, status, ${Timestamps.iso(sql"computed_at")} FROM event_link_candidates"
        .query[(String, String, String, String)]
        .run()
        .map((e, c, st, at) => (e, c) -> (st, at))
        .toMap
      w.es.eventCandidates.foreach { c =>
        for id <- eventIds(c.eventUid); ck <- w.citekeyHere(c.sourceUid) do
          val passages = BundleFormat.print(c.passages)
          here.get((id.value, ck.value)) match
            case None =>
              sql"""INSERT INTO event_link_candidates (event_id, citekey, score, status, findings, passages, reasons, computed_at, creator)
                    VALUES ($id, $ck, ${c.score}, ${c.status}, ${c.findings}::text[], $passages::jsonb,
                      ${c.reasons}::text[], ${c.computedAt}::timestamptz, ${StoredCreator.write(
                  Creator.unrecorded
                )}::jsonb)""".update.run(): Unit
            case Some((status, at)) =>
              val keep = Bundle.EventSets.candidateStatus(Bundle.EventCandidate.Status(status), c.status)
              if newer(Some(Bundle.UpdatedAt(at)), Some(Bundle.UpdatedAt(c.computedAt.value))) then
                sql"""UPDATE event_link_candidates SET score = ${c.score}, status = $keep, findings = ${c.findings}::text[],
                        passages = $passages::jsonb, reasons = ${c.reasons}::text[], computed_at = ${c.computedAt}::timestamptz
                      WHERE event_id = $id AND citekey = $ck""".update.run(): Unit
              else if keep.value != status then
                sql"UPDATE event_link_candidates SET status = $keep WHERE event_id = $id AND citekey = $ck".update
                  .run(): Unit
      }

  /** When the candidates were last computed: the newer check. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def checksIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    val eventIds = eventIdsOf()
    import com.histmersion.api.exchange.Bundle.EventSets.newer
    if w.es.eventChecks.nonEmpty then
      val here = sql"SELECT event_id, ${Timestamps.iso(sql"checked_at")} FROM event_link_checks"
        .query[(String, String)]
        .run()
        .toMap
      w.es.eventChecks.foreach { k =>
        eventIds(k.eventUid)
          .filter(id => newer(here.get(id.value).map(Bundle.UpdatedAt(_)), Some(Bundle.UpdatedAt(k.checkedAt.value))))
          .foreach { id =>
            sql"""INSERT INTO event_link_checks (event_id, checked_at) VALUES ($id, ${k.checkedAt}::timestamptz)
                ON CONFLICT (event_id) DO UPDATE SET checked_at = EXCLUDED.checked_at""".update.run()
          }
      }

  /** This collection's own attestations a merge moved into the body it kept, or dropped: written as this collection's
    * own rows, so their clocks move and a drop leaves a tombstone.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def attestationFixes(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    if w.merged.attestations.nonEmpty then
      w.asLocal {
        w.merged.attestations.foreach { f =>
          if f.drop.drop then
            Unused(sql"DELETE FROM attestations WHERE uid = ${f.uid}".update.run(), Unused.Why.Idempotent)
          else
            Unused(
              sql"""UPDATE attestations SET char_start = ${f.anchor.map(_.start)}, char_end = ${f.anchor.map(_.end)},
                    chunk_ord = ${f.chunkOrd} WHERE uid = ${f.uid}""".update.run(),
              Unused.Why.Idempotent
            )
        }
      }

  /** The attestations, once their sources and what they attest are in: a target by its label here. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def attestationsIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    w.es.attestations.filter(t => w.writes(t.uid)).foreach { t =>
      val ck = w.citekeyOf(t.sourceUid)
      val target = w.targetLabel(
        BundleImport.Writer.TargetType(t.targetType.value),
        BundleImport.Writer.TargetId(t.targetId.value),
        t.targetUid
      )
      sql"""INSERT INTO attestations (uid, origin, citekey, target_type, target_id, quote, prefix, suffix,
              char_start, char_end, chunk_ord, page, canvas, xywh, cts_urn, note, created_at, updated_at, creator)
            VALUES (${t.uid}, ${t.origin}, $ck, ${t.targetType}, $target, ${t.quote}, ${t.prefix}, ${t.suffix},
              ${t.charStart}, ${t.charEnd}, ${t.chunkOrd}, ${t.page}, ${t.canvas}, ${t.xywh}, ${t.ctsUrn},
              ${t.note}, ${t.createdAt}::timestamptz, ${t.updatedAt}::timestamptz,
              ${StoredCreator.carried(t.creator)}::jsonb)
            ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, citekey = EXCLUDED.citekey,
              target_type = EXCLUDED.target_type, target_id = EXCLUDED.target_id, quote = EXCLUDED.quote,
              prefix = EXCLUDED.prefix, suffix = EXCLUDED.suffix, char_start = EXCLUDED.char_start,
              char_end = EXCLUDED.char_end, chunk_ord = EXCLUDED.chunk_ord, page = EXCLUDED.page,
              canvas = EXCLUDED.canvas, xywh = EXCLUDED.xywh, cts_urn = EXCLUDED.cts_urn,
              note = EXCLUDED.note, created_at = EXCLUDED.created_at,
              updated_at = EXCLUDED.updated_at""".update.run()
    }
