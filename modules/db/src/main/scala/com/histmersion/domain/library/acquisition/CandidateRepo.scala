package com.histmersion.domain.library.acquisition

import com.histmersion.util.database.given

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.api.library.{CandidateCount, CandidateWatch}
import com.histmersion.api.library.Source

/** The deterministic new-source search's store (V099, #3761): each goal's watches with their watermarks, and the
  * candidates found, one per goal and DOI. Every method runs on the connection it is given.
  */
private[acquisition] object CandidateRepo:

  type WatchesGoal = WatchesGoal.T
  object WatchesGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordWatchWatermark = RecordWatchWatermark.T
  object RecordWatchWatermark extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordWatchError = RecordWatchError.T
  object RecordWatchError extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordWatchFound = RecordWatchFound.T
  object RecordWatchFound extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpsertResult = UpsertResult.T
  object UpsertResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DoisResult = DoisResult.T
  object DoisResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DoisGoal = DoisGoal.T
  object DoisGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListGoal = ListGoal.T
  object ListGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListState = ListState.T
  object ListState extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListLimit = ListLimit.T
  object ListLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListOffset = ListOffset.T
  object ListOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountsGoal = CountsGoal.T
  object CountsGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkResult = MarkResult.T
  object MarkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkIds = MarkIds.T
  object MarkIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkState = MarkState.T
  object MarkState extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkDoisResult = MarkDoisResult.T
  object MarkDoisResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkDoisGoal = MarkDoisGoal.T
  object MarkDoisGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkDoisDois = MarkDoisDois.T
  object MarkDoisDois extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkDoisState = MarkDoisState.T
  object MarkDoisState extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val ts = """'YYYY-MM-DD"T"HH24:MI:SS"Z"'"""

  final private case class WatchRow(
      goal: WatchRow.Goal,
      kind: WatchRow.Kind,
      subject: WatchRow.Subject,
      index: WatchRow.Index,
      watermark: Option[WatchRow.Watermark],
      lastRun: Option[WatchRow.LastRun],
      lastError: Option[WatchRow.LastError],
      lastFound: WatchRow.LastFound
  ) derives DbCodec

  object WatchRow:
    type Goal = Goal.T
    object Goal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Subject = Subject.T
    object Subject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Index = Index.T
    object Index extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Watermark = Watermark.T
    object Watermark extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastRun = LastRun.T
    object LastRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastError = LastError.T
    object LastError extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastFound = LastFound.T
    object LastFound extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A goal's watches as they were last read. */
  def watches(goal: com.histmersion.domain.library.acquisition.CandidateRepo.WatchesGoal)(using DbCon): List[CandidateWatch] =
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
      watermark: Option[com.histmersion.domain.library.acquisition.CandidateRepo.RecordWatchWatermark],
      error: Option[com.histmersion.domain.library.acquisition.CandidateRepo.RecordWatchError],
      found: com.histmersion.domain.library.acquisition.CandidateRepo.RecordWatchFound
  )(using DbCon): Unit =
    val _ =
      sql"""INSERT INTO candidate_watches (goal, kind, subject, index_name, watermark, last_run, last_error, last_found)
                    VALUES (${w.goal}, ${w.kind}, ${w.subject}, ${w.index}, $watermark::date, now(), $error, $found)
                    ON CONFLICT (goal, kind, subject, index_name) DO UPDATE SET
                      watermark = coalesce(EXCLUDED.watermark, candidate_watches.watermark), last_run = now(),
                      last_error = EXCLUDED.last_error, last_found = EXCLUDED.last_found""".update.run()

  /** Store a candidate: a new one (`true`), or one found again — every watch that found it kept, its state as it was.
    */
  def upsert(c: Source.Candidate)(using DbCon): com.histmersion.domain.library.acquisition.CandidateRepo.UpsertResult =
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
  def dois(goal: com.histmersion.domain.library.acquisition.CandidateRepo.DoisGoal)(using DbCon): Set[com.histmersion.domain.library.acquisition.CandidateRepo.DoisResult] =
    sql"SELECT doi FROM source_candidates WHERE goal = $goal".query[String].run().toSet

  final private case class Row(
      id: Row.Id,
      goal: Row.Goal,
      doi: com.histmersion.domain.library.Doi,
      title: Row.Title,
      year: Option[com.histmersion.Year],
      authors: List[Row.Authors],
      venue: Option[Row.Venue],
      abstractText: Option[Row.AbstractText],
      isOa: Row.IsOa,
      arxivId: Option[Row.ArxivId],
      foundBy: List[Row.FoundBy],
      state: Row.State,
      firstSeen: Row.FirstSeen,
      lastSeen: Row.LastSeen
  ) derives DbCodec

  object Row:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Goal = Goal.T
    object Goal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Authors = Authors.T
    object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Venue = Venue.T
    object Venue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AbstractText = AbstractText.T
    object AbstractText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IsOa = IsOa.T
    object IsOa extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArxivId = ArxivId.T
    object ArxivId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FoundBy = FoundBy.T
    object FoundBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type State = State.T
    object State extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FirstSeen = FirstSeen.T
    object FirstSeen extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastSeen = LastSeen.T
    object LastSeen extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A page of candidates of one goal (every goal: `None`) in one state, oldest found first. */
  def list(goal: Option[com.histmersion.domain.library.acquisition.CandidateRepo.ListGoal], state: com.histmersion.domain.library.acquisition.CandidateRepo.ListState, limit: com.histmersion.domain.library.acquisition.CandidateRepo.ListLimit, offset: com.histmersion.domain.library.acquisition.CandidateRepo.ListOffset)(using DbCon): List[Source.Candidate] =
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
          (r.venue).map(com.histmersion.api.library.Source.Venue.apply),
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
  def counts(goal: Option[com.histmersion.domain.library.acquisition.CandidateRepo.CountsGoal])(using DbCon): List[CandidateCount] =
    sql"""SELECT state, count(*)::int FROM source_candidates WHERE ($goal::text IS NULL OR goal = $goal)
            GROUP BY state ORDER BY CASE state WHEN 'new' THEN 0 WHEN 'seen' THEN 1 WHEN 'queued' THEN 2 ELSE 3 END"""
      .query[(String, Int)]
      .run()
      .toList
      .map(CandidateCount(_, _))

  /** Set the state of these candidates; how many there were. */
  def mark(ids: List[com.histmersion.domain.library.acquisition.CandidateRepo.MarkIds], state: com.histmersion.domain.library.acquisition.CandidateRepo.MarkState)(using DbCon): com.histmersion.domain.library.acquisition.CandidateRepo.MarkResult =
    if ids.isEmpty then 0
    else
      val arr = ids.map(_.toString)
      sql"UPDATE source_candidates SET state = $state WHERE id = ANY($arr::bigint[])".update.run()

  /** Set the state of a goal's candidates with these DOIs (a ranking's picks). */
  def markDois(goal: com.histmersion.domain.library.acquisition.CandidateRepo.MarkDoisGoal, dois: List[com.histmersion.domain.library.acquisition.CandidateRepo.MarkDoisDois], state: com.histmersion.domain.library.acquisition.CandidateRepo.MarkDoisState)(using DbCon): com.histmersion.domain.library.acquisition.CandidateRepo.MarkDoisResult =
    if dois.isEmpty then 0
    else sql"UPDATE source_candidates SET state = $state WHERE goal = $goal AND doi = ANY($dois::text[])".update.run()
