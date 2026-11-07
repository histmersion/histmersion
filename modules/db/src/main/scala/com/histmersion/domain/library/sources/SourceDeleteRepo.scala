package com.histmersion.domain.library.sources

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.Write
import com.histmersion.domain.library.sources.referrers.{DeleteReport, SourceKeys, SourceReferrers}

import com.histmersion.util.database.given

import com.augustnagro.magnum.*
import com.histmersion.api.library.{DeleteCount, DeletePart, Edge}
import com.histmersion.api.Api
import com.histmersion.api.library.Source

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
private[sources] object SourceDeleteRepo:
  type LogTopic = LogTopic.T
  object LogTopic extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Delete `requested`, unless something refuses it. `files` names the stored originals the service removes after the
    * commit (they are part of the answer and the log line); `logTopic` is the log entry's heading (today's date).
    */
  type KeysOfKeys = _root_.com.histmersion.domain.library.sources.referrers.SourceKeys.Keys
  val KeysOfKeys: _root_.com.histmersion.domain.library.sources.referrers.SourceKeys.Keys.type = _root_.com.histmersion.domain.library.sources.referrers.SourceKeys.Keys

  def applyIn(requested: com.histmersion.domain.library.Citekey, logTopic: com.histmersion.domain.library.sources.SourceDeleteRepo.LogTopic, files: SourceDeletePlan => List[String], referrers: SourceReferrers)(
      using DbCon
  ): Either[Api.Error, SourceDeletePlan] =
    val _ = sql"SELECT citekey FROM sources WHERE citekey = $requested FOR UPDATE".query[String].run()
    planIn(requested, referrers).flatMap { p0 =>
      val d0 = p0.deletion
      if d0.refused then Left(Api.Error(Source.Delete.refusal(d0.citekey, d0.blockers), Some(Write.CodeInvalid)))
      else
        val p = p0.modify(_.deletion).setTo(d0.modify(_.files).setTo(files(p0)))
        val source = keysOf(p.deletion.citekey, p.keys)
        deleteNow(p, source, referrers)
        val d = p.deletion.modify(_.applied).setTo(true)
        val log = referrers.recorded(source, Source.Delete.logText(d), logTopic)
        Right(p.modify(_.deletion).setTo(d.modify(_.logEntry).setTo(log)))
    }

  final private case class Row(citekey: com.histmersion.domain.library.Citekey, title: Row.Title, calibreId: Option[Row.CalibreId], sha256: Option[com.histmersion.Sha256])
      derives DbCodec

  object Row:
    type Title = com.histmersion.domain.Title
    val Title: _root_.com.histmersion.domain.Title.type = _root_.com.histmersion.domain.Title
    type CalibreId = CalibreId.T
    object CalibreId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def keysOf(ck: com.histmersion.domain.library.Citekey, keys: List[com.histmersion.domain.library.sources.SourceDeleteRepo.KeysOfKeys])(using DbCon): SourceKeys =
    SourceKeys(ck, keys, targets => SourceRepo.resolveAliases(targets))

  def planIn(requested: com.histmersion.domain.library.Citekey, referrers: SourceReferrers)(using DbCon): Either[Api.Error, SourceDeletePlan] =
    sql"SELECT citekey, title, calibre_id, sha256 FROM sources WHERE citekey = $requested"
      .query[Row]
      .run()
      .headOption match
      case None =>
        Left(sql"SELECT citekey FROM source_aliases WHERE alias = $requested".query[String].run().headOption match
          case Some(now) =>
            Api.Error(s"'$requested' is an old citekey of '$now' — delete '$now' instead", Some(Write.CodeInvalid))
          case None => Write.notFound(requested))
      case Some(r) =>
        val ck = r.citekey
        val aliases = sql"""SELECT alias FROM source_aliases WHERE citekey = $ck ORDER BY alias COLLATE "C""""
          .query[String]
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
          .map((s, d, k) => Edge(com.histmersion.api.library.Citekey(s), com.histmersion.api.library.Citekey(d), k))
        val report = referrers.report(DeleteReport(counts = counts(ck, aliases.size, edgesIn.size)), keysOf(ck, keys))
        val d = Source.Deletion(
          citekey = ck,
          title = r.title,
          applied = com.histmersion.api.library.Source.Deletion.Applied(false),
          blockers = report.blockers,
          counts = report.counts,
          aliases = aliases,
          edgesIn = edgesIn,
          coverage = report.coverage,
          mentions = report.mentions,
          calibreId = r.calibreId.map(_.trim).filter(_.nonEmpty)
        )
        Right(SourceDeletePlan(d, r.sha256.map(_.trim).filter(_.nonEmpty), shared, keys, report))

  private def n(q: Frag)(using DbCon): com.histmersion.RowCount = q.query[Int].run().headOption.getOrElse(0)

  /** What of the library goes with the source. */
  private def counts(ck: com.histmersion.domain.library.Citekey, aliases: com.histmersion.RowCount, edgesIn: com.histmersion.RowCount)(using DbCon): List[DeleteCount] =
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
    ).collect { case (p, rows) if rows > 0 => DeleteCount(p, rows) }

  private def deleteNow(p: SourceDeletePlan, source: SourceKeys, referrers: SourceReferrers)(using DbCon): Unit =
    val ck = p.deletion.citekey
    // other sources' edges to it are PARKED, exactly as an edge to a work not in the corpus is (V026):
    // they are that source's data, and come back if this one is ingested again
    sql"""INSERT INTO unresolved_edges (src, dst, kind)
          SELECT src, dst, kind FROM edges WHERE dst = $ck AND src <> $ck
          ON CONFLICT DO NOTHING""".update.run()
    referrers.deleting(source, p.report)
    // the rest goes by ON DELETE CASCADE; the row's trigger leaves its tombstone (V031)
    sql"DELETE FROM sources WHERE citekey = $ck".update.run()
