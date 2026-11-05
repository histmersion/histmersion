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
      chunkChars: com.histmersion.domain.library.sources.SourceRepairs.ReindexChunksChunkChars,
      after: Option[com.histmersion.domain.library.sources.SourceRepairs.ReindexChunksAfter] = None,
      maxSources: Option[com.histmersion.domain.library.sources.SourceRepairs.ReindexChunksMaxSources] = None,
      maxChars: com.histmersion.domain.library.sources.SourceRepairs.ReindexChunksMaxChars = Long.MaxValue
  ): ReindexResult =
    val pending = db.read(SourceRepo.bodiesAfter(after))
    val take = SourceRepairs.budget(pending, maxChars).take(maxSources.getOrElse(Int.MaxValue)) match
      case Nil   => pending.take(1).map(_._1).toList
      case batch => batch
    val counts = take.map(ck => db.transact(SourceRepo.rechunk(ck, chunkChars)))
    ReindexResult(take.size, counts.sum, take.lastOption, pending.size - take.size)

  /** Rebuild every chunk in one go (tests, offline tools) — no budget, so it can run for minutes. */
  def reindexAll(chunkChars: com.histmersion.domain.library.sources.SourceRepairs.ReindexAllChunkChars): ReindexResult =
    @annotation.tailrec
    def go(after: Option[com.histmersion.domain.library.sources.SourceRepairs.ReindexAllGoAfter], sources: com.histmersion.domain.library.sources.SourceRepairs.ReindexAllGoSources, chunks: com.histmersion.domain.library.sources.SourceRepairs.ReindexAllGoChunks): ReindexResult =
      val r = reindexChunks(chunkChars, after)
      val (s, c) = (sources + r.sources, chunks + r.chunks)
      if r.remaining > 0 && r.lastCitekey.isDefined then go(r.lastCitekey, s, c)
      else ReindexResult(s, c, r.lastCitekey, r.remaining)
    go(None, 0, 0)

  /** Re-chunk the sources that have a body but no full-text chunks (`stale-chunks`).
    *
    * Bounded by `maxChars` of body text per call — a whole-corpus rebuild takes minutes, far past any HTTP timeout —
    * and always does at least one source, so repeated calls make progress. Returns the same shape as `/api/reindex`,
    * with `remaining` = stale sources still untouched.
    */
  def reindexStale(chunkChars: com.histmersion.domain.library.sources.SourceRepairs.ReindexStaleChunkChars, maxChars: com.histmersion.domain.library.sources.SourceRepairs.ReindexStaleMaxChars): ReindexResult =
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
    LintFixResult(List(s"resolve-edges: $resolved edge target(s) rewritten"), resolved, 0, 0, 0)

  /** `stale-chunks`: re-chunk a bounded batch of the sources with a body and no chunks. */
  def reindexFix(cfg: AppConfig): LintFixResult =
    val r = reindexStale(cfg.limits.chunkChars, cfg.limits.reindexChars)
    val left = if r.remaining > 0 then s", ${r.remaining} still stale" else ""
    LintFixResult(
      List(s"reindex: ${r.sources} source(s) → ${r.chunks} chunk(s)$left"),
      0,
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
      0,
      0,
      0,
      0,
      findingsRegeocoded = moved
    )

  /** `finding-unplaced`: give the findings the rule lists the point of the place they name. */
  def placeFix(cfg: AppConfig): LintFixResult =
    val s = SourceCheckRepo.geocodeOf(cfg)
    val placed = db.transact(FindingPlacement.place(FindingPlacement.placementsIn(s), s.toleranceKm))
    LintFixResult(
      List(s"$FindingUnplaced: $placed finding(s) given the point of the place they name"),
      0,
      0,
      0,
      0,
      findingsPlaced = placed
    )

object SourceRepairs:

  /** The citekeys of `pending` (in order) whose bodies fit in `maxChars` together. */
  type ReindexAllGoAfter = ReindexAllGoAfter.T
  object ReindexAllGoAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReindexAllGoSources = ReindexAllGoSources.T
  object ReindexAllGoSources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReindexAllGoChunks = ReindexAllGoChunks.T
  object ReindexAllGoChunks extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BudgetLoopResult = BudgetLoopResult.T
  object BudgetLoopResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BudgetLoopRestPart1 = BudgetLoopRestPart1.T
  object BudgetLoopRestPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BudgetLoopRestPart2 = BudgetLoopRestPart2.T
  object BudgetLoopRestPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BudgetLoopSpent = BudgetLoopSpent.T
  object BudgetLoopSpent extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BudgetLoopOut = BudgetLoopOut.T
  object BudgetLoopOut extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ReindexChunksChunkChars = ReindexChunksChunkChars.T
  object ReindexChunksChunkChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReindexChunksAfter = ReindexChunksAfter.T
  object ReindexChunksAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReindexChunksMaxSources = ReindexChunksMaxSources.T
  object ReindexChunksMaxSources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReindexChunksMaxChars = ReindexChunksMaxChars.T
  object ReindexChunksMaxChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReindexAllChunkChars = ReindexAllChunkChars.T
  object ReindexAllChunkChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReindexStaleChunkChars = ReindexStaleChunkChars.T
  object ReindexStaleChunkChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReindexStaleMaxChars = ReindexStaleMaxChars.T
  object ReindexStaleMaxChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BudgetResult = BudgetResult.T
  object BudgetResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BudgetPendingPart1 = BudgetPendingPart1.T
  object BudgetPendingPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BudgetPendingPart2 = BudgetPendingPart2.T
  object BudgetPendingPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BudgetMaxChars = BudgetMaxChars.T
  object BudgetMaxChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def budget(pending: Vector[(com.histmersion.domain.library.sources.SourceRepairs.BudgetPendingPart1, com.histmersion.domain.library.sources.SourceRepairs.BudgetPendingPart2)], maxChars: com.histmersion.domain.library.sources.SourceRepairs.BudgetMaxChars): List[com.histmersion.domain.library.sources.SourceRepairs.BudgetResult] =
    @annotation.tailrec
    def loop(rest: List[(com.histmersion.domain.library.sources.SourceRepairs.BudgetLoopRestPart1, com.histmersion.domain.library.sources.SourceRepairs.BudgetLoopRestPart2)], spent: com.histmersion.domain.library.sources.SourceRepairs.BudgetLoopSpent, out: List[com.histmersion.domain.library.sources.SourceRepairs.BudgetLoopOut]): List[com.histmersion.domain.library.sources.SourceRepairs.BudgetLoopResult] = rest match
      case (ck, len) :: tail if spent + len.toLong <= maxChars => loop(tail, spent + len.toLong, ck :: out)
      case _                                                   => out.reverse
    loop(pending.toList, 0L, Nil)
