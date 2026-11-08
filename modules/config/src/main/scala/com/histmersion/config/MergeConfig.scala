package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** The source matcher of `merge plan` (docs/research-merge-import.md §5.2): Fellegi–Sunter-style log-weights with
  * hand-set values, and the two thresholds that split a score into auto-merge, needs-decision and new. Tuned here, not
  * in code.
  */
final case class MergeConfig(
    /** A score at or above this, with no hard flag, merges without asking. */
    autoMerge: MergeConfig.AutoMerge,
    /** A score at or below this is not the same work. */
    newAtMost: MergeConfig.NewAtMost,
    /** MinHash functions per OCR body. */
    minhashSize: MergeConfig.MinhashSize,
    /** Two findings of a merged pair are near-duplicates at this claim-word Jaccard (with overlapping periods): kept
      * apart by default, listed so the user can say `merge`.
      */
    findingNearAtLeast: MergeConfig.FindingNearAtLeast,
    /** Two findings of a merged pair are at the same place within this many km (§5.4). */
    findingWithinKm: MergeConfig.FindingWithinKm,
    weights: MergeWeights,
    /** A place of the bundle with no id here is the same as a place here with its label and kind when the two points
      * are this close (km)…
      */
    placeWithinKm: MergeConfig.PlaceWithinKm = MergeConfig.PlaceWithinKm(5.0),
    /** …or the two shapes overlap this much (intersection over union). */
    placeOverlapAtLeast: MergeConfig.PlaceOverlapAtLeast = MergeConfig.PlaceOverlapAtLeast(0.8),
    /** How long `POST /api/merge/apply` waits for the apply (its backup first) before it answers `running` with the job
      * to follow (`GET /api/merge/apply/{job}`).
      */
    applyWaitSeconds: MergeConfig.ApplyWaitSeconds = MergeConfig.ApplyWaitSeconds(20)
) derives ConfigReader

object MergeConfig:
  type AutoMerge = AutoMerge.T
  object AutoMerge extends Newtype[Double, Pure]
  type NewAtMost = NewAtMost.T
  object NewAtMost extends Newtype[Double, Pure]
  type MinhashSize = MinhashSize.T
  object MinhashSize extends Newtype[Int, Pure]
  type FindingNearAtLeast = FindingNearAtLeast.T
  object FindingNearAtLeast extends Newtype[Double, Pure]
  type FindingWithinKm = FindingWithinKm.T
  object FindingWithinKm extends Newtype[Double, Pure]
  type PlaceWithinKm = PlaceWithinKm.T
  object PlaceWithinKm extends Newtype[Double, Pure]
  type PlaceOverlapAtLeast = PlaceOverlapAtLeast.T
  object PlaceOverlapAtLeast extends Newtype[Double, Pure]
  type ApplyWaitSeconds = ApplyWaitSeconds.T
  object ApplyWaitSeconds extends Newtype[Int, Pure]
