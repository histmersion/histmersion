package com.histmersion.domain.library.sources

import com.histmersion.api.checks.Lint.*
import com.histmersion.config.AppConfig
import com.histmersion.domain.catalogue.places.FindingPlacement
import com.histmersion.util.database.Transactor
import com.histmersion.api.maintenance.ReindexResult

/** The library's repairs that run as several transactions: the re-chunk of the corpus and of the stale sources (one
  * transaction per source, so a large corpus never sits in one) and the safe fixes of `lint --fix`.
  */
final class SourceRepairs(db: Transactor):

  /** Rebuild the chunk index — `POST /api/reindex`. Needed once after V004 (the existing corpus was imported before
    * chunking existed) and after any bulk edit of `ocr_markdown` that bypassed a source write. One transaction per
    * source, so an 89 MB corpus never sits in memory.
    *
    * RESUMABLE, because the whole corpus takes minutes (Postgres spends ~2 ms per chunk on the generated tsvector plus
    * the GIN insert — 38 000 chunks ≈ 2.5 min) and the server's request timeout is 20 s: sources are walked in citekey
    * order from `after`, and the call stops once `maxChars` of body text have been re-chunked (always at least one
    * source, so progress is guaranteed). The result carries the cursor and how many sources are left.
    */
  def reindexChunks(
      chunkChars: com.histmersion.util.ocr.Chunker.ChunkMaxChars,
      after: Option[com.histmersion.domain.library.Citekey] = None,
      maxSources: Option[com.histmersion.domain.library.sources.SourceRepairs.MaxSources] = None,
      maxChars: com.histmersion.domain.library.sources.SourceRepairs.MaxChars = Long.MaxValue
  ): ReindexResult =
    val pending = db.read(SourceRepo.bodiesAfter(after))
    val take = SourceRepairs.budget(pending, maxChars).take(maxSources.getOrElse(Int.MaxValue)) match
      case Nil   => pending.take(1).map(_._1).toList
      case batch => batch
    val counts = take.map(ck => db.transact(SourceRepo.rechunk(ck, chunkChars)))
    ReindexResult(take.size, counts.sum, take.lastOption, pending.size - take.size)

  /** Rebuild every chunk in one go (tests, offline tools) — no budget, so it can run for minutes. */
  def reindexAll(chunkChars: com.histmersion.util.ocr.Chunker.ChunkMaxChars): ReindexResult =
    @annotation.tailrec
    def go(after: Option[com.histmersion.domain.library.Citekey], sources: com.histmersion.RowCount, chunks: com.histmersion.RowCount): ReindexResult =
      val r = reindexChunks(chunkChars, after)
      val (s, c) = (sources + r.sources, chunks + r.chunks)
      if r.remaining > 0 && r.lastCitekey.isDefined then go(r.lastCitekey, s, c)
      else ReindexResult(s, c, r.lastCitekey, r.remaining)
    go(None, com.histmersion.RowCount(0), com.histmersion.RowCount(0))

  /** Re-chunk the sources that have a body but no full-text chunks (`stale-chunks`).
    *
    * Bounded by `maxChars` of body text per call — a whole-corpus rebuild takes minutes, far past any HTTP timeout —
    * and always does at least one source, so repeated calls make progress. Returns the same shape as `/api/reindex`,
    * with `remaining` = stale sources still untouched.
    */
  def reindexStale(chunkChars: com.histmersion.util.ocr.Chunker.ChunkMaxChars, maxChars: com.histmersion.domain.library.sources.SourceRepairs.MaxChars): ReindexResult =
    val pending = db.read(SourceCheckRepo.stale())
    val take = SourceRepairs.budget(pending, maxChars) match
      case Nil   => pending.take(1).map(_._1).toList
      case batch => batch
    // Best effort, one transaction per source: another writer may re-chunk or remove a source between
    // the scan and the rewrite, and a bulk repair that aborts on one row is worse than one that keeps
    // going. A skipped source stays stale, so the next `lint` reports it again — nothing is hidden.
    val done = take.map(ck => scala.util.Try(db.transact(SourceRepo.rechunk(ck, chunkChars))))
    done.zip(take).foreach {
      case (scala.util.Failure(e), ck) => scribe.warn(s"reindex of $ck skipped: ${e.getMessage}")
      case _                           => ()
    }
    val ok = done.collect { case scala.util.Success(n) => n }
    ReindexResult(ok.size, ok.sum, take.lastOption, pending.size - take.size)

  // ── the safe fixes of `lint --fix`, each as the part of the result it reports ─────────────────

  /** `unresolved-short-citekeys`: unify the unambiguous short edge targets. */
  def resolveEdgesFix(): LintFixResult =
    val resolved = db.transact(SourceRepo.resolveEdges())
    LintFixResult(List(s"resolve-edges: $resolved edge target(s) rewritten"), resolved, com.histmersion.api.checks.Lint.LintFixResult.SourcesReindexed(0), com.histmersion.api.checks.Lint.LintFixResult.ChunksWritten(0), com.histmersion.api.checks.Lint.LintFixResult.Remaining(0))

  /** `stale-chunks`: re-chunk a bounded batch of the sources with a body and no chunks. */
  def reindexFix(cfg: AppConfig): LintFixResult =
    val r = reindexStale(cfg.limits.chunkChars, cfg.limits.reindexChars)
    val left = if r.remaining > 0 then s", ${r.remaining} still stale" else ""
    LintFixResult(
      List(s"reindex: ${r.sources} source(s) → ${r.chunks} chunk(s)$left"),
      com.histmersion.api.checks.Lint.LintFixResult.EdgesResolved(0),
      r.sources,
      r.chunks,
      r.remaining
    )

  /** `finding-geocode`: move the findings the rule lists to the place the current geocoder chooses. */
  def regeocodeFix(cfg: AppConfig): LintFixResult =
    val s = SourceCheckRepo.geocodeOf(cfg)
    val moved = db.transact(FindingPlacement.move(FindingPlacement.movesIn(s), s.toleranceKm))
    LintFixResult(
      List(s"$FindingGeocode: $moved finding(s) moved to the place the current geocoder chooses"),
      com.histmersion.api.checks.Lint.LintFixResult.EdgesResolved(0),
      com.histmersion.api.checks.Lint.LintFixResult.SourcesReindexed(0),
      com.histmersion.api.checks.Lint.LintFixResult.ChunksWritten(0),
      com.histmersion.api.checks.Lint.LintFixResult.Remaining(0),
      findingsRegeocoded = moved
    )

  /** `finding-unplaced`: give the findings the rule lists the point of the place they name. */
  def placeFix(cfg: AppConfig): LintFixResult =
    val s = SourceCheckRepo.geocodeOf(cfg)
    val placed = db.transact(FindingPlacement.place(FindingPlacement.placementsIn(s), s.toleranceKm))
    LintFixResult(
      List(s"$FindingUnplaced: $placed finding(s) given the point of the place they name"),
      com.histmersion.api.checks.Lint.LintFixResult.EdgesResolved(0),
      com.histmersion.api.checks.Lint.LintFixResult.SourcesReindexed(0),
      com.histmersion.api.checks.Lint.LintFixResult.ChunksWritten(0),
      com.histmersion.api.checks.Lint.LintFixResult.Remaining(0),
      findingsPlaced = placed
    )

object SourceRepairs:
  type CharsSpent = CharsSpent.T
  object CharsSpent extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxChars = MaxChars.T
  object MaxChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxSources = MaxSources.T
  object MaxSources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The citekeys of `pending` (in order) whose bodies fit in `maxChars` together. */


  def budget(pending: Vector[(com.histmersion.domain.library.Citekey, com.histmersion.RowCount)], maxChars: com.histmersion.domain.library.sources.SourceRepairs.MaxChars): List[String] =
    @annotation.tailrec
    def loop(rest: List[(com.histmersion.domain.library.Citekey, com.histmersion.RowCount)], spent: com.histmersion.domain.library.sources.SourceRepairs.CharsSpent, out: List[String]): List[String] = rest match
      case (ck, len) :: tail if spent + len.toLong <= maxChars => loop(tail, spent + len.toLong, ck :: out)
      case _                                                   => out.reverse
    loop(pending.toList, com.histmersion.domain.library.sources.SourceRepairs.CharsSpent(0L), Nil)
