package com.histmersion
package domain.library.sources
import com.histmersion.util.problem.Problem

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.Write
import com.histmersion.domain.library.sources.referrers.{DeleteReport, SourceKeys, SourceReferrers}

import com.histmersion.util.database.given

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.api.library.{DeleteCount, DeletePart, Edge}
import com.histmersion.api.Api
import com.histmersion.api.library.Source
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.Unused
import com.histmersion.domain.{CreatedAt, Creator}
import com.histmersion.util.database.StoredCreator
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** Deleting a source (#969) — the database half of `POST /api/sources/{ck}/delete`. The rules are
  * [[com.histmersion.api.library.SourceDelete]]'s; this reads what a delete would touch ([[planIn]]) and does it
  * ([[applyIn]]), in the caller's transaction.
  *
  * What goes by `ON DELETE CASCADE` with the `sources` row (findings and their place assignments, edges both ways,
  * parked edges from it, chunks, figures and their links, its attestations, the assessment, old citekeys, identifiers)
  * is COUNTED here first, so the answer says what went; what other domains hold of it is theirs to report and remove
  * (the referrers). The `sources` row's tombstone (V031) is written by its trigger, so a bundle merge does not bring
  * the source back.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@atBoundary(BoundaryKind.Sql, "reads and writes the source delete tables and their row codecs")
private[sources] object SourceDeleteRepo:
  /** Delete `requested`, unless something refuses it. `files` names the stored originals the service removes after the
    * commit (they are part of the answer and the log line); `logTopic` is the log entry's heading (today's date).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def applyIn(
      requested: Citekey,
      logTopic: SourceReferrers.Day,
      files: SourceDeletePlan => List[Source.Deletion.Files],
      referrers: SourceReferrers
  )(using
      DbCon
  )(using Creator.New, CreatedAt): Either[Api.Error, SourceDeletePlan] =
    val _ = sql"SELECT citekey FROM sources WHERE citekey = $requested FOR UPDATE".query[String].run()
    planIn(requested, referrers).flatMap { p0 =>
      val d0 = p0.deletion
      if d0.refused then
        Left(
          Api.Error(
            Source.Delete.refusal(d0.citekey, d0.blockers),
            Some(Api.Error.Code(Write.CodeInvalid))
          )
        )
      else
        val p = p0.modify(_.deletion).setTo(d0.modify(_.files).setTo(files(p0)))
        val source = keysOf(requested, p.keys.flatMap(k => Citekey.option(k.value)))
        deleteNow(p, source, referrers)
        val d = p.deletion.modify(_.applied).setTo(Source.Deletion.Applied(true))
        val log = referrers.recorded(source, SourceReferrers.Line(Source.Delete.logText(d).value), logTopic)
        Right(p.modify(_.deletion).setTo(d.modify(_.logEntry).setTo(log.map(e => Source.Deletion.LogEntry(e.value)))))
    }

  final private case class Row(
      citekey: Citekey,
      title: Row.Title,
      calibreId: Option[Row.CalibreId],
      sha256: Option[SourceDeletePlan.Sha256]
  ) derives DbCodec

  object Row:
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type Title = domain.Title
    val Title: domain.Title.type = domain.Title
    type CalibreId = CalibreId.T
    object CalibreId extends Newtype[String, Pure]

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def keysOf(ck: Citekey, keys: List[Citekey])(using DbCon): SourceKeys =
    SourceKeys(ck, keys, targets => SourceRepo.resolveAliases(targets))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def planIn(requested: Citekey, referrers: SourceReferrers)(using DbCon): Either[Api.Error, SourceDeletePlan] =
    sql"SELECT citekey, title, calibre_id, sha256 FROM sources WHERE citekey = $requested"
      .query[Row]
      .run()
      .headOption match
      case None =>
        Left(sql"SELECT citekey FROM source_aliases WHERE alias = $requested".query[String].run().headOption match
          case Some(now) =>
            Api.Error(
              Problem.Conflict.OldKey(
                Problem.TypeName.of(Citekey),
                Problem.Raw.of(requested),
                Problem.Raw(s"$now"),
                Problem.Conflict.Action.Delete
              ),
              Some(Api.Error.Code(Write.CodeInvalid))
            )
          case None => Write.notFound(com.histmersion.api.library.Citekey(requested.value)))
      case Some(r) =>
        val ck = r.citekey
        val aliases = sql"""SELECT alias FROM source_aliases WHERE citekey = $ck ORDER BY alias COLLATE "C""""
          .query[Citekey]
          .run()
          .toList
        val keys = ck :: aliases
        val shared = r.sha256.exists(sha =>
          sql"SELECT citekey FROM sources WHERE sha256 = $sha AND citekey <> $ck LIMIT 1".query[String].run().nonEmpty
        )
        val edgesIn = sql"""SELECT src, dst, kind FROM edges WHERE dst = $ck AND src <> $ck ORDER BY src, kind"""
          .query[(String, String, String)]
          .run()
          .toList
          .flatMap((s, d, k) =>
            Edge.Kind
              .fromWire(Edge.Kind.Wire(k))
              .map(kind => Edge(com.histmersion.api.library.Citekey(s), com.histmersion.api.library.Citekey(d), kind))
          )
        val report = referrers.report(
          DeleteReport(counts = counts(ck, RowCount(aliases.size), RowCount(edgesIn.size))),
          keysOf(ck, keys)
        )
        val d = Source.Deletion(
          citekey = com.histmersion.api.library.Citekey(ck.value),
          title = com.histmersion.api.library.Title(r.title.value),
          applied = Source.Deletion.Applied(false),
          blockers = report.blockers,
          counts = report.counts,
          aliases = aliases.map(a => Source.Deletion.Aliases(a.value)),
          edgesIn = edgesIn,
          coverage = report.coverage.map(c => Source.Deletion.Coverage(c.value)),
          mentions = report.mentions.map(m => Source.Deletion.Mentions(m.value)),
          calibreId = r.calibreId.map(_.value.trim).filter(_.nonEmpty).map(Source.CalibreId(_))
        )
        Right(
          SourceDeletePlan(
            d,
            r.sha256.map(_.value.trim).filter(_.nonEmpty).map(SourceDeletePlan.Sha256(_)),
            SourceDeletePlan.ShaShared(shared),
            keys.map(k => SourceDeletePlan.Keys(k.value)),
            report
          )
        )

  private def n(q: Frag)(using DbCon): RowCount = RowCount(q.query[Int].run().headOption.getOrElse(0))

  /** What of the library goes with the source. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def counts(ck: Citekey, aliases: RowCount, edgesIn: RowCount)(using DbCon): List[DeleteCount] =
    import com.histmersion.api.library.DeletePart.*
    List(
      Findings -> n(sql"SELECT count(*)::int FROM findings WHERE citekey = $ck"),
      EdgesOut -> n(sql"SELECT count(*)::int FROM edges WHERE src = $ck"),
      ParkedOut -> n(sql"SELECT count(*)::int FROM unresolved_edges WHERE src = $ck"),
      EdgesIn -> edgesIn,
      Chunks -> n(sql"SELECT count(*)::int FROM source_chunks WHERE citekey = $ck"),
      Assessments -> n(sql"SELECT count(*)::int FROM assessments WHERE citekey = $ck"),
      Aliases -> aliases,
      Identifiers -> n(sql"""SELECT count(*)::int FROM source_identifiers i JOIN sources s ON s.uid = i.source_uid
                               WHERE s.citekey = $ck""")
    ).collect { case (p, rows) if rows.value > 0 => DeleteCount(p, DeleteCount.Rows(rows.value)) }

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def deleteNow(p: SourceDeletePlan, source: SourceKeys, referrers: SourceReferrers)(using
      DbCon
  )(using creator: Creator.New): Unit =
    val ck = p.deletion.citekey
    // other sources' edges to it are PARKED, exactly as an edge to a work not in the corpus is (V026):
    // they are that source's data, and come back if this one is ingested again
    Unused(
      sql"""INSERT INTO unresolved_edges (src, dst, kind, creator)
          SELECT src, dst, kind, ${StoredCreator.column(creator)}::jsonb FROM edges WHERE dst = $ck AND src <> $ck
          ON CONFLICT DO NOTHING""".update.run(),
      Unused.Why.Idempotent
    )
    referrers.deleting(source, p.report)
    // the rest goes by ON DELETE CASCADE; the row's trigger leaves its tombstone (V031)
    Unused(sql"DELETE FROM sources WHERE citekey = $ck".update.run(), Unused.Why.Held)
