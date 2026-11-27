package com.histmersion
package domain.library.sources

import com.histmersion.api.checks.Lint.*
import com.histmersion.config.AppConfig
import com.histmersion.domain.catalogue.places.FindingPlacement
import com.histmersion.util.database.Transactor
import com.histmersion.api.maintenance.ReindexResult
import com.histmersion.util.ocr.Chunker
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** The library's repairs that run as several transactions: the re-chunk of the corpus and of the stale sources (one
  * transaction per source, so a large corpus never sits in one) and the safe fixes of `lint --fix`.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def reindexChunks(
      chunkChars: Chunker.ChunkMaxChars,
      after: Option[Citekey] = None,
      maxSources: Option[SourceRepairs.MaxSources] = None,
      maxChars: SourceRepairs.MaxChars = SourceRepairs.MaxChars(Long.MaxValue)
  )(using WritePermit): ReindexResult =
    val pending = db.read(SourceRepo.bodiesAfter(after))
    val take = SourceRepairs.budget(pending, maxChars).take(maxSources.map(_.value).getOrElse(Int.MaxValue)) match
      case Nil   => pending.take(1).map(_._1).toList
      case batch => batch
    val counts = take.map(ck => db.write(SourceRepo.rechunk(ck, chunkChars)))
    ReindexResult(
      ReindexResult.Sources(take.size),
      ReindexResult.Chunks(counts.map(_.value).sum),
      take.lastOption.map(ck => com.histmersion.api.library.Citekey(ck.value)),
      ReindexResult.Remaining(pending.size - take.size)
    )

  /** Rebuild every chunk in one go (tests, offline tools) — no budget, so it can run for minutes. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def reindexAll(chunkChars: Chunker.ChunkMaxChars)(using WritePermit): ReindexResult =
    @annotation.tailrec
    def go(after: Option[Citekey], sources: Int, chunks: Int)(using WritePermit): ReindexResult =
      val r = reindexChunks(chunkChars, after)
      val (s, c) = (sources + r.sources.value, chunks + r.chunks.value)
      if r.remaining.value > 0 && r.lastCitekey.isDefined then
        go(r.lastCitekey.map(ck => Citekey.assume(ck.value)), s, c)
      else ReindexResult(ReindexResult.Sources(s), ReindexResult.Chunks(c), r.lastCitekey, r.remaining)
    go(None, 0, 0)

  /** Re-chunk the sources that have a body but no full-text chunks (`stale-chunks`).
    *
    * Bounded by `maxChars` of body text per call — a whole-corpus rebuild takes minutes, far past any HTTP timeout —
    * and always does at least one source, so repeated calls make progress. Returns the same shape as `/api/reindex`,
    * with `remaining` = stale sources still untouched.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def reindexStale(chunkChars: Chunker.ChunkMaxChars, maxChars: SourceRepairs.MaxChars)(using
      WritePermit
  ): ReindexResult =
    val pending = db.read(SourceCheckRepo.stale())
    val take = SourceRepairs.budget(pending.map((ck, n) => (ck, SourceRepo.BodyChars(n.value))), maxChars) match
      case Nil   => pending.take(1).map(_._1).toList
      case batch => batch
    // Best effort, one transaction per source: another writer may re-chunk or remove a source between
    // the scan and the rewrite, and a bulk repair that aborts on one row is worse than one that keeps
    // going. A skipped source stays stale, so the next `lint` reports it again — nothing is hidden.
    val done = take.map(ck => scala.util.Try(db.write(SourceRepo.rechunk(ck, chunkChars))))
    done.zip(take).foreach {
      case (scala.util.Failure(e), ck) => scribe.warn(s"reindex of ${ck.value} skipped: ${e.getMessage}")
      case _                           => ()
    }
    val ok = done.collect { case scala.util.Success(n) => n }
    ReindexResult(
      ReindexResult.Sources(ok.size),
      ReindexResult.Chunks(ok.map(_.value).sum),
      take.lastOption.map(ck => com.histmersion.api.library.Citekey(ck.value)),
      ReindexResult.Remaining(pending.size - take.size)
    )

  // ── the safe fixes of `lint --fix`, each as the part of the result it reports ─────────────────

  /** `unresolved-short-citekeys`: unify the unambiguous short edge targets. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def resolveEdgesFix()(using WritePermit)(using Creator.New): LintFixResult =
    val resolved = db.write(SourceRepo.resolveEdges())
    LintFixResult(
      List(LintFixResult.Applied(s"resolve-edges: ${resolved.value} edge target(s) rewritten")),
      LintFixResult.EdgesResolved(resolved.value),
      LintFixResult.SourcesReindexed(0),
      LintFixResult.ChunksWritten(0),
      LintFixResult.Remaining(0)
    )

  /** `stale-chunks`: re-chunk a bounded batch of the sources with a body and no chunks. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def reindexFix(cfg: AppConfig)(using WritePermit): LintFixResult =
    val r = reindexStale(
      Chunker.ChunkMaxChars(cfg.limits.chunkChars.value),
      SourceRepairs.MaxChars(cfg.limits.reindexChars.value)
    )
    val left = if r.remaining.value > 0 then s", ${r.remaining.value} still stale" else ""
    LintFixResult(
      List(LintFixResult.Applied(s"reindex: ${r.sources.value} source(s) → ${r.chunks.value} chunk(s)$left")),
      LintFixResult.EdgesResolved(0),
      LintFixResult.SourcesReindexed(r.sources.value),
      LintFixResult.ChunksWritten(r.chunks.value),
      LintFixResult.Remaining(r.remaining.value)
    )

  /** `finding-geocode`: move the findings the rule lists to the place the current geocoder chooses. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def regeocodeFix(cfg: AppConfig)(using WritePermit): LintFixResult =
    val s = SourceCheckRepo.geocodeOf(cfg)
    val moved = db.write(
      FindingPlacement.move(
        FindingPlacement.movesIn(s),
        FindingPlacement.Implementation.ToleranceKm(s.toleranceKm.value)
      )
    )
    LintFixResult(
      List(
        LintFixResult.Applied(
          s"$FindingGeocode: ${moved.value} finding(s) moved to the place the current geocoder chooses"
        )
      ),
      LintFixResult.EdgesResolved(0),
      LintFixResult.SourcesReindexed(0),
      LintFixResult.ChunksWritten(0),
      LintFixResult.Remaining(0),
      findingsRegeocoded = LintFixResult.FindingsRegeocoded(moved.value)
    )

  /** `finding-unplaced`: give the findings the rule lists the point of the place they name. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def placeFix(cfg: AppConfig)(using WritePermit): LintFixResult =
    val s = SourceCheckRepo.geocodeOf(cfg)
    val placed = db.write(
      FindingPlacement.place(
        FindingPlacement.placementsIn(s),
        FindingPlacement.Implementation.ToleranceKm(s.toleranceKm.value)
      )
    )
    LintFixResult(
      List(
        LintFixResult.Applied(s"$FindingUnplaced: ${placed.value} finding(s) given the point of the place they name")
      ),
      LintFixResult.EdgesResolved(0),
      LintFixResult.SourcesReindexed(0),
      LintFixResult.ChunksWritten(0),
      LintFixResult.Remaining(0),
      findingsPlaced = LintFixResult.FindingsPlaced(placed.value)
    )

object SourceRepairs:
  type MaxChars = MaxChars.T
  object MaxChars extends Newtype[Long, Pure]
  type MaxSources = MaxSources.T
  object MaxSources extends Newtype[Int, Pure]

  /** The citekeys of `pending` (in order) whose bodies fit in `maxChars` together. */

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def budget(pending: Vector[(Citekey, SourceRepo.BodyChars)], maxChars: MaxChars): List[Citekey] =
    @annotation.tailrec
    def loop(rest: List[(Citekey, SourceRepo.BodyChars)], spent: Long, out: List[Citekey]): List[Citekey] = rest match
      case (ck, len) :: tail if spent + len.value.toLong <= maxChars.value =>
        loop(tail, spent + len.value.toLong, ck :: out)
      case _ => out.reverse
    loop(pending.toList, 0L, Nil)
