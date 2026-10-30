package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The source matcher of `merge plan` (docs/research-merge-import.md §5.2): Fellegi–Sunter-style log-weights with
  * hand-set values, and the two thresholds that split a score into auto-merge, needs-decision and new. Tuned here, not
  * in code.
  */
final case class MergeConfig(
    /** A score at or above this, with no hard flag, merges without asking. */
    autoMerge: Double,
    /** A score at or below this is not the same work. */
    newAtMost: Double,
    /** MinHash functions per OCR body. */
    minhashSize: Int,
    /** Two findings of a merged pair are near-duplicates at this claim-word Jaccard (with overlapping periods): kept
      * apart by default, listed so the user can say `merge`.
      */
    findingNearAtLeast: Double,
    /** Two findings of a merged pair are at the same place within this many km (§5.4). */
    findingWithinKm: Double,
    weights: MergeWeights,
    /** A place of the bundle with no id here is the same as a place here with its label and kind when the two points
      * are this close (km)…
      */
    placeWithinKm: Double = 5.0,
    /** …or the two shapes overlap this much (intersection over union). */
    placeOverlapAtLeast: Double = 0.8,
    /** How long `POST /api/merge/apply` waits for the apply (its backup first) before it answers `running` with the job
      * to follow (`GET /api/merge/apply/{job}`).
      */
    applyWaitSeconds: Int = 20
) derives ConfigReader
