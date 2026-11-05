package com.histmersion.domain.library.acquisition

import com.histmersion.util.database.given

import com.augustnagro.magnum.*
import com.histmersion.api.library.{CandidateCount, CandidateWatch}
import com.histmersion.api.library.Source

/** The deterministic new-source search's store (V099, #3761): each goal's watches with their watermarks, and the
  * candidates found, one per goal and DOI. Every method runs on the connection it is given.
  */
private[acquisition] object CandidateRepo:

  private val ts = """'YYYY-MM-DD"T"HH24:MI:SS"Z"'"""

  final private case class WatchRow(
      goal: String,
      kind: String,
      subject: String,
      index: String,
      watermark: Option[String],
      lastRun: Option[String],
      lastError: Option[String],
      lastFound: Int
  ) derives DbCodec

  /** A goal's watches as they were last read. */
  def watches(goal: String)(using DbCon): List[CandidateWatch] =
    sql"""SELECT goal, kind, subject, index_name, to_char(watermark, 'YYYY-MM-DD'),
                   to_char(last_run AT TIME ZONE 'UTC', ${SqlLiteral(ts)}), last_error, last_found
            FROM candidate_watches WHERE goal = $goal ORDER BY kind, subject, index_name"""
      .query[WatchRow]
      .run()
      .toList
      .map(r => CandidateWatch(r.goal, r.kind, r.subject, r.index, r.watermark, r.lastRun, r.lastError, r.lastFound))

  /** A watch read: its new watermark (on success; a failed read keeps the old one, so nothing is skipped), what it
    * found, or why it failed.
    */
  def recordWatch(
      w: CandidateWatch,
      watermark: Option[String],
      error: Option[String],
      found: Int
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
  def dois(goal: String)(using DbCon): Set[String] =
    sql"SELECT doi FROM source_candidates WHERE goal = $goal".query[String].run().toSet

  final private case class Row(
      id: Long,
      goal: String,
      doi: String,
      title: String,
      year: Option[Int],
      authors: List[String],
      venue: Option[String],
      abstractText: Option[String],
      isOa: Boolean,
      arxivId: Option[String],
      foundBy: List[String],
      state: String,
      firstSeen: String,
      lastSeen: String
  ) derives DbCodec

  /** A page of candidates of one goal (every goal: `None`) in one state, oldest found first. */
  def list(goal: Option[String], state: String, limit: Int, offset: Int)(using DbCon): List[Source.Candidate] =
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
          com.histmersion.api.library.Doi(r.doi),
          com.histmersion.api.library.Title(r.title),
          (r.year).map(com.histmersion.api.catalogue.Year.apply),
          (r.authors).map(com.histmersion.api.library.AuthorName.apply),
          (r.venue).map(com.histmersion.api.library.Venue.apply),
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
  def counts(goal: Option[String])(using DbCon): List[CandidateCount] =
    sql"""SELECT state, count(*)::int FROM source_candidates WHERE ($goal::text IS NULL OR goal = $goal)
            GROUP BY state ORDER BY CASE state WHEN 'new' THEN 0 WHEN 'seen' THEN 1 WHEN 'queued' THEN 2 ELSE 3 END"""
      .query[(String, Int)]
      .run()
      .toList
      .map(CandidateCount(_, _))

  /** Set the state of these candidates; how many there were. */
  def mark(ids: List[Long], state: String)(using DbCon): Int =
    if ids.isEmpty then 0
    else
      val arr = ids.map(_.toString)
      sql"UPDATE source_candidates SET state = $state WHERE id = ANY($arr::bigint[])".update.run()

  /** Set the state of a goal's candidates with these DOIs (a ranking's picks). */
  def markDois(goal: String, dois: List[String], state: String)(using DbCon): Int =
    if dois.isEmpty then 0
    else sql"UPDATE source_candidates SET state = $state WHERE goal = $goal AND doi = ANY($dois::text[])".update.run()
