package com.histmersion
package domain.library.acquisition
import io.scalaland.chimney.dsl.*

import com.histmersion.domain.Problem
import com.histmersion.util.database.given

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.api.library.{CandidateCount, CandidateWatch}
import com.histmersion.api.library.Source
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.library.Doi
import com.histmersion.domain.Title
import com.histmersion.domain.AuthorName

/** The deterministic new-source search's store (V099, #3761): each goal's watches with their watermarks, and the
  * candidates found, one per goal and DOI. Every method runs on the connection it is given.
  */
private[acquisition] object CandidateRepo:
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]

  private val ts = """'YYYY-MM-DD"T"HH24:MI:SS"Z"'"""

  final private case class WatchRow(
      goal: CandidateWatch.Goal,
      kind: CandidateWatch.Kind,
      subject: CandidateWatch.Subject,
      index: CandidateWatch.Index,
      watermark: Option[CandidateWatch.Watermark],
      lastRun: Option[CandidateWatch.LastRun],
      lastError: Option[Problem],
      lastFound: CandidateWatch.LastFound
  ) derives DbCodec

  /** A goal's watches as they were last read. */
  def watches(goal: Goal.Slug)(using DbCon): List[CandidateWatch] =
    sql"""SELECT goal, kind, subject, index_name, to_char(watermark, 'YYYY-MM-DD'),
                   to_char(last_run AT TIME ZONE 'UTC', ${SqlLiteral(ts)}), last_error, last_found
            FROM candidate_watches WHERE goal = $goal ORDER BY kind, subject, index_name"""
      .query[WatchRow]
      .run()
      .toList
      .map(r => CandidateWatch(r.goal, r.kind, r.subject, r.index, r.watermark, r.lastRun, r.lastError.map(_.transformInto[api.Problem]), r.lastFound))

  /** A watch read: its new watermark (on success; a failed read keeps the old one, so nothing is skipped), what it
    * found, or why it failed.
    */
  def recordWatch(
      w: CandidateWatch,
      watermark: Option[CandidateWatch.Watermark],
      error: Option[Problem],
      found: CandidateWatch.LastFound
  )(using DbCon): Unit =
    val _ =
      sql"""INSERT INTO candidate_watches (goal, kind, subject, index_name, watermark, last_run, last_error, last_found)
                    VALUES (${w.goal}, ${w.kind}, ${w.subject}, ${w.index}, $watermark::date, now(), $error, $found)
                    ON CONFLICT (goal, kind, subject, index_name) DO UPDATE SET
                      watermark = coalesce(EXCLUDED.watermark, candidate_watches.watermark), last_run = now(),
                      last_error = EXCLUDED.last_error, last_found = EXCLUDED.last_found""".update.run()

  /** Store a candidate: a new one (`true`), or one found again — every watch that found it kept, its state as it was.
    */
  def upsert(c: Source.Candidate)(using DbCon): Boolean =
    val existed =
      sql"SELECT 1 FROM source_candidates WHERE goal = ${c.goal} AND doi = ${c.doi}".query[Int].run().nonEmpty
    val _ =
      sql"""INSERT INTO source_candidates (goal, doi, title, year, authors, venue, abstract, is_oa, arxiv_id, found_by)
                    VALUES (${c.goal}, ${c.doi}, ${c.title}, ${c.year}, ${c.authors}::text[], ${c.venue}, ${c.abstractText},
                            ${c.isOa}, ${c.arxivId}, ${c.foundBy}::text[])
                    ON CONFLICT (goal, doi) DO UPDATE SET last_seen = now(),
                      found_by = (SELECT array_agg(DISTINCT f) FROM unnest(source_candidates.found_by || EXCLUDED.found_by) f),
                      abstract = coalesce(source_candidates.abstract, EXCLUDED.abstract),
                      is_oa = source_candidates.is_oa OR EXCLUDED.is_oa""".update.run()
    !existed

  /** The DOIs a goal has candidates for already (any state): a sweep does not count them as new. */
  def dois(goal: Goal.Slug)(using DbCon): Set[Doi] =
    sql"SELECT doi FROM source_candidates WHERE goal = $goal".query[Doi].run().toSet

  final private case class Row(
      id: Source.Candidate.Id,
      goal: Source.Candidate.Goal,
      doi: Doi,
      title: Title,
      year: Option[Year],
      authors: List[AuthorName],
      venue: Option[Source.Venue],
      abstractText: Option[Source.Candidate.AbstractText],
      isOa: Source.Candidate.IsOa,
      arxivId: Option[Source.Candidate.ArxivId],
      foundBy: List[Source.Candidate.FoundBy],
      state: Source.Candidate.State,
      firstSeen: Source.Candidate.FirstSeen,
      lastSeen: Source.Candidate.LastSeen
  ) derives DbCodec

  /** A page of candidates of one goal (every goal: `None`) in one state, oldest found first. */
  def list(goal: Option[Goal.Slug], state: Source.Candidate.State, limit: Limit, offset: Offset)(using
      DbCon
  ): List[Source.Candidate] =
    sql"""SELECT id, goal, doi, title, year, authors, venue, abstract, is_oa, arxiv_id, found_by, state,
                   to_char(first_seen AT TIME ZONE 'UTC', ${SqlLiteral(
        ts
      )}), to_char(last_seen AT TIME ZONE 'UTC', ${SqlLiteral(ts)})
            FROM source_candidates WHERE ($goal::text IS NULL OR goal = $goal) AND state = $state
            ORDER BY id LIMIT $limit OFFSET $offset"""
      .query[Row]
      .run()
      .toList
      .map(r =>
        Source.Candidate(
          r.id,
          r.goal,
          com.histmersion.api.library.Doi(r.doi.value),
          com.histmersion.api.library.Title(r.title.value),
          r.year.map(y => com.histmersion.api.catalogue.Year(y.value.toLong)),
          r.authors.map(a => com.histmersion.api.library.AuthorName(a.value)),
          r.venue,
          r.abstractText,
          r.isOa,
          r.arxivId,
          r.foundBy,
          r.state,
          r.firstSeen,
          r.lastSeen
        )
      )

  /** How many candidates there are in each state (of one goal, or of all). */
  def counts(goal: Option[Goal.Slug])(using DbCon): List[CandidateCount] =
    sql"""SELECT state, count(*)::int FROM source_candidates WHERE ($goal::text IS NULL OR goal = $goal)
            GROUP BY state ORDER BY CASE state WHEN 'new' THEN 0 WHEN 'seen' THEN 1 WHEN 'queued' THEN 2 ELSE 3 END"""
      .query[(Source.Candidate.State, CandidateCount.Count)]
      .run()
      .toList
      .map(CandidateCount(_, _))

  /** Set the state of these candidates; how many there were. */
  def mark(ids: List[Source.Candidate.Id], state: Source.Candidate.State)(using DbCon): RowCount =
    if ids.isEmpty then RowCount(0)
    else
      val arr = ids.map(_.toString)
      RowCount(sql"UPDATE source_candidates SET state = $state WHERE id = ANY($arr::bigint[])".update.run())

  /** Set the state of a goal's candidates with these DOIs (a ranking's picks). */
  def markDois(goal: Goal.Slug, dois: List[Doi], state: Source.Candidate.State)(using DbCon): RowCount =
    if dois.isEmpty then RowCount(0)
    else
      RowCount(
        sql"UPDATE source_candidates SET state = $state WHERE goal = $goal AND doi = ANY($dois::text[])".update.run()
      )
