package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.domain.automation.worklists.QueueRepo
import javax.sql.DataSource

/** Deleting a source (#969) — the database half of `POST /api/sources/{ck}/delete`. The rules are
  * [[com.histmersion.api.SourceDelete]]'s; this reads what a delete would touch ([[plan]]) and does it in ONE
  * transaction ([[apply]]).
  *
  * What goes by `ON DELETE CASCADE` with the `sources` row (findings and their place assignments, edges both ways,
  * parked edges from it, chunks, figures and their links, its attestations, the assessment, old citekeys, identifiers)
  * is COUNTED here first, so the answer says what went. The columns that name a citekey without a foreign key are
  * handled explicitly — the same list a rename rewrites (`SourceRepo.renameNow`): event datings go, the events and
  * relations sourced to it refuse the delete, jobs and gazetteer provenance are history and stay. The `sources` row's
  * tombstone (V031) is written by its trigger, so a bundle merge does not bring the source back.
  */
object SourceDeleteRepo:

  /** The answer, plus what the handler needs to find the stored original: its digest, and whether another source still
    * holds the same file (then the file stays). `keys` = the citekey and its old citekeys — the acquisition spool is
    * keyed by whichever one the file came in under.
    */
  final case class Plan(deletion: SourceDeletion, sha256: Option[String], shaShared: Boolean, keys: List[String])

  /** What a delete would do — read-only. */
  def plan(ds: DataSource, requested: String): Either[ApiError, Plan] = connect(ds)(planIn(requested))

  /** Delete `requested` in one transaction, unless something refuses it. `files` names the stored originals the handler
    * removes after the commit (they are part of the answer and the log line); `logTopic` is the log queue's entry
    * heading (today's date).
    */
  def apply(ds: DataSource, requested: String, logTopic: String, files: Plan => List[String]): Either[ApiError, Plan] =
    transact(ds):
      val _ = sql"SELECT citekey FROM sources WHERE citekey = $requested FOR UPDATE".query[String].run()
      planIn(requested).flatMap { p0 =>
        val d0 = p0.deletion
        if d0.refused then Left(ApiError(SourceDelete.refusal(d0.citekey, d0.blockers), Some(Write.CodeInvalid)))
        else
          val p = p0.copy(deletion = d0.copy(files = files(p0)))
          deleteNow(p)
          val d = p.deletion.copy(applied = true)
          val log = QueueRepo.insert(Queue.Kind.Log, Queue.QueueAdd(SourceDelete.logText(d), topic = Some(logTopic)))
          Right(p.copy(deletion = d.copy(logEntry = Some(log.id))))
      }

  final private case class Row(citekey: String, title: String, calibreId: Option[String], sha256: Option[String])
      derives DbCodec

  private[db] def planIn(requested: String)(using DbCon): Either[ApiError, Plan] =
    sql"SELECT citekey, title, calibre_id, sha256 FROM sources WHERE citekey = $requested"
      .query[Row]
      .run()
      .headOption match
      case None =>
        Left(sql"SELECT citekey FROM source_aliases WHERE alias = $requested".query[String].run().headOption match
          case Some(now) =>
            ApiError(s"'$requested' is an old citekey of '$now' — delete '$now' instead", Some(Write.CodeInvalid))
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
          .map((s, d, k) => Edge(s, d, k))
        val d = SourceDeletion(
          citekey = ck,
          title = r.title,
          applied = false,
          blockers = blockers(ck, keys),
          counts = counts(ck, keys, aliases.size, edgesIn.size),
          aliases = aliases,
          edgesIn = edgesIn,
          coverage = coverageArticles(keys),
          mentions = mentions(keys),
          calibreId = r.calibreId.map(_.trim).filter(_.nonEmpty)
        )
        Right(Plan(d, r.sha256.map(_.trim).filter(_.nonEmpty), shared, keys))

  // ── what refuses it ──────────────────────────────────────────────────────────────────────────

  private def blockers(ck: String, keys: List[String])(using DbCon): List[DeleteBlocker] =
    val articles =
      citingArticles(ck, keys).map((slug, title) => DeleteBlocker(DeleteBlockerKind.Article, slug, Some(title)))
    val events = sql"SELECT id, label FROM events WHERE citekey = ANY($keys::text[]) ORDER BY id"
      .query[(String, String)]
      .run()
      .toList
      .map((id, l) => DeleteBlocker(DeleteBlockerKind.Event, id, Some(l)))
    val relations = sql"""SELECT src_id, kind, dst_id FROM event_edges WHERE citekey = ANY($keys::text[])
                          ORDER BY src_id, kind, dst_id"""
      .query[(String, String, String)]
      .run()
      .toList
      .map((s, k, d) => DeleteBlocker(DeleteBlockerKind.EventEdge, s"$s $k $d"))
    val participants = sql"""SELECT event_id, actor FROM event_participants WHERE citekey = ANY($keys::text[])
                             ORDER BY event_id, actor"""
      .query[(String, String)]
      .run()
      .toList
      .map((e, a) => DeleteBlocker(DeleteBlockerKind.EventParticipant, e, Some(a)))
    // an ingest job or an agent run that has not finished would write the source back
    val jobs = sql"""SELECT id, kind FROM jobs
                     WHERE status IN ('queued', 'running', 'paused', 'waiting')
                       AND (citekey = ANY($keys::text[]) OR (target_kind = 'source' AND target_id = ANY($keys::text[])))
                     ORDER BY created_at"""
      .query[(String, String)]
      .run()
      .toList
      .map((id, k) => DeleteBlocker(DeleteBlockerKind.Job, id, Some(k)))
    articles ++ events ++ relations ++ participants ++ jobs

  /** The articles whose body or summary cites the source: the recorded link graph (exact and old citekeys, resolved at
    * write time), and a scan of the text for the short forms the renderer resolves to it (`[[beckman2025]]` for
    * `beckman2025_ahhiyawa` while that is unambiguous).
    */
  private def citingArticles(ck: String, keys: List[String])(using DbCon): List[(String, String)] =
    val linked = sql"""SELECT a.slug, a.title FROM article_links l JOIN articles a ON a.slug = l.src_slug
                       WHERE l.target_kind = 'source' AND l.target = $ck"""
      .query[(String, String)]
      .run()
      .toList
    val needles = keys.map(_.toLowerCase.takeWhile(_ != '_')).distinct
    val scanned = sql"""SELECT slug, title, body, summary FROM articles
                        WHERE EXISTS (SELECT 1 FROM unnest($needles::text[]) AS n(needle)
                                      WHERE strpos(lower(body || ' ' || coalesce(summary, '')), n.needle) > 0)"""
      .query[(String, String, String, Option[String])]
      .run()
      .toList
      .flatMap { (slug, title, body, summary) =>
        val targets = (ArticleLinks.targets(body) ++ summary.toList.flatMap(ArticleLinks.targets)).distinct
        val resolved = SourceRepo.resolveAliases(targets)
        Option.when(resolved.values.exists(_ == ck))(slug -> title)
      }
    (linked ++ scanned).distinctBy(_._1).sortBy(_._1)

  // ── what goes ────────────────────────────────────────────────────────────────────────────────

  private def n(q: Frag)(using DbCon): Int = q.query[Int].run().headOption.getOrElse(0)

  /** Other sources' attestations whose target goes with the source ([[SourceDelete.attestationGone]]). */
  private def attestationsOf(ck: String)(using DbCon): List[Long] =
    sql"""SELECT id, target_type, target_id FROM attestations
          WHERE citekey <> $ck AND target_type IN ('finding', 'edge', 'event_dating') AND strpos(target_id, $ck) > 0"""
      .query[(Long, String, String)]
      .run()
      .toList
      .collect { case (id, t, target) if SourceDelete.attestationGone(t, target, ck) => id }

  /** Other figures' links to the source or one of its findings. */
  private def assetLinksTo(ck: String, keys: List[String])(using DbCon): List[Long] =
    val hash = ck + "#"
    sql"""SELECT id FROM asset_links WHERE citekey <> $ck
            AND ((target_type = 'source' AND target_id = ANY($keys::text[]))
              OR (target_type = 'finding' AND left(target_id, ${hash.length}) = $hash))"""
      .query[Long]
      .run()
      .toList

  private def counts(ck: String, keys: List[String], aliases: Int, edgesIn: Int)(using DbCon): List[DeleteCount] =
    import DeletePart.*
    List(
      Findings -> n(sql"SELECT count(*)::int FROM findings WHERE citekey = $ck"),
      FindingPlaces -> n(sql"""SELECT count(*)::int FROM finding_places fp JOIN findings f ON f.id = fp.finding_id
                               WHERE f.citekey = $ck"""),
      EdgesOut -> n(sql"SELECT count(*)::int FROM edges WHERE src = $ck"),
      ParkedOut -> n(sql"SELECT count(*)::int FROM unresolved_edges WHERE src = $ck"),
      EdgesIn -> edgesIn,
      Chunks -> n(sql"SELECT count(*)::int FROM source_chunks WHERE citekey = $ck"),
      Assets -> n(sql"SELECT count(*)::int FROM assets WHERE citekey = $ck"),
      AssetLinks -> n(sql"SELECT count(*)::int FROM asset_links WHERE citekey = $ck"),
      AssetLinksTo -> assetLinksTo(ck, keys).size,
      Attestations -> n(sql"SELECT count(*)::int FROM attestations WHERE citekey = $ck"),
      AttestationsOf -> attestationsOf(ck).size,
      Assessments -> n(sql"SELECT count(*)::int FROM assessments WHERE citekey = $ck"),
      Aliases -> aliases,
      Identifiers -> n(sql"""SELECT count(*)::int FROM source_identifiers i JOIN sources s ON s.uid = i.source_uid
                               WHERE s.citekey = $ck"""),
      EventDatings -> n(sql"SELECT count(*)::int FROM event_datings WHERE citekey = ANY($keys::text[])"),
      GoalLinks -> n(sql"SELECT count(*)::int FROM goal_links WHERE kind = 'source' AND ref = ANY($keys::text[])"),
      CoverageReasons -> coverageArticles(keys).size
    ).collect { case (p, rows) if rows > 0 => DeleteCount(p, rows) }

  /** Articles whose coverage reasons (V066) are keyed by the source. */
  private def coverageArticles(keys: List[String])(using DbCon): List[String] =
    sql"""SELECT slug FROM articles a
          WHERE EXISTS (SELECT 1 FROM jsonb_object_keys(a.coverage_reasons) AS k(key) WHERE k.key = ANY($keys::text[]))
          ORDER BY slug""".query[String].run().toList

  /** Queue items (any list) whose text or reason links the source — reported, never changed. */
  private def mentions(keys: List[String])(using DbCon): List[Int] =
    val needles = keys.map(_.toLowerCase)
    sql"""SELECT id, text, coalesce(reason, '') FROM queue_items
          WHERE status <> 'note'
            AND EXISTS (SELECT 1 FROM unnest($needles::text[]) AS n(needle)
                        WHERE strpos(lower(text || ' ' || coalesce(reason, '')), n.needle) > 0)
          ORDER BY id"""
      .query[(Int, String, String)]
      .run()
      .toList
      .collect { case (id, text, reason) if keys.exists(k => SourceDelete.links(text + "\n" + reason, k)) => id }

  // ── the delete ───────────────────────────────────────────────────────────────────────────────

  private def deleteNow(p: Plan)(using DbCon): Unit =
    val ck = p.deletion.citekey
    val keys = p.keys
    // other sources' edges to it are PARKED, exactly as an edge to a work not in the corpus is (V026):
    // they are that source's data, and come back if this one is ingested again
    sql"""INSERT INTO unresolved_edges (src, dst, kind)
          SELECT src, dst, kind FROM edges WHERE dst = $ck AND src <> $ck
          ON CONFLICT DO NOTHING""".update.run()
    val gone = attestationsOf(ck).map(_.toString)
    if gone.nonEmpty then sql"DELETE FROM attestations WHERE id::text = ANY($gone::text[])".update.run()
    val links = assetLinksTo(ck, keys).map(_.toString)
    if links.nonEmpty then sql"DELETE FROM asset_links WHERE id::text = ANY($links::text[])".update.run()
    sql"DELETE FROM event_datings WHERE citekey = ANY($keys::text[])".update.run()
    sql"DELETE FROM goal_links WHERE kind = 'source' AND ref = ANY($keys::text[])".update.run()
    if p.deletion.coverage.nonEmpty then
      sql"""UPDATE articles SET coverage_reasons = coverage_reasons - $keys::text[], updated_at = now()
            WHERE slug = ANY(${p.deletion.coverage}::text[])""".update.run()
    // the rest goes by ON DELETE CASCADE; the row's trigger leaves its tombstone (V031)
    sql"DELETE FROM sources WHERE citekey = $ck".update.run()
