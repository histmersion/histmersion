package com.histmersion
package util.data.exchange
import com.histmersion.util.problem.{Problem, Problems}

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.exchange.{MergePlan, MergeResolutions}
import com.histmersion.util.data.exchange.MergeState
import com.histmersion.domain.{MergePlanner, MergeSettings}
import com.histmersion.util.storage.{ByteSource, ContentStore}
import com.histmersion.util.database.Transactor

import com.augustnagro.magnum.*
import javax.sql.DataSource
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.text.MinHash
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

/** How a merge plan is put together (docs/research-merge-import.md §5–§6, milestone M3): the bundle read, what this
  * collection knows gathered ([[MergeState]]), the OCR bodies the matcher and the folds need — this collection's from
  * the database, the bundle's from one pass over its blobs — and the pure planner over all of it.
  *
  * Read inside the caller's transaction — `merge plan` in a read-only `REPEATABLE READ` snapshot ([[planFile]]),
  * `bundle import` and `merge apply` in their own write transaction, so the plan they check is the plan of the state
  * they write over.
  */
@tolerated(LintRule.Helper, "pending G63 (no free-standing helper objects)")
@atBoundary(BoundaryKind.Sql, "reads the database state a merge plan is built from")
object MergePlans:

  /** `merge plan`: read the bundle, gather this collection's state in one read-only snapshot, plan. */

  def planFile(
      ds: DataSource,
      bundle: ByteSource,
      resolutions: MergeResolutions,
      cfg: MergeSettings,
      files: ContentStore,
      chunkChars: ImportOptions.ChunkChars,
      kinds: CarriedKinds,
      rules: MergeRuleSet
  ): Either[Problems, MergePlan] =
    val loaded = bundle.read(BundleReader.read(_, hashBlobs = false))
    loaded.manifest match
      case None    => Left(loaded.unreadable)
      case Some(m) =>
        Right(Transactor(ds).snapshot {
          sql"SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY".update.run()
          MergeState.withGeoJson(plan(bundle, m, loaded, resolutions, cfg, files, chunkChars, kinds, rules)._2.plan)
        })

  /** The plan, inside the caller's transaction. `chunkChars` places a re-anchored attestation in its FTS chunk;
    * `merge plan` and `merge apply` must pass the same value, or their plan hashes differ.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def plan(
      bundle: ByteSource,
      m: com.histmersion.api.exchange.Bundle.Manifest,
      loaded: BundleReader.Loaded,
      resolutions: MergeResolutions,
      cfg: MergeSettings,
      files: ContentStore,
      chunkChars: ImportOptions.ChunkChars,
      kinds: CarriedKinds,
      rules: MergeRuleSet
  )(using DbCon): (MergePlanner.Input, MergePlanner.Result) =
    val input0 = MergeState.load(
      loaded.entities,
      m,
      loaded.problems,
      files,
      chunkChars,
      cfg,
      kinds,
      rules
    )
    val sk =
      MergeState.sketches(
        bundle,
        input0,
        MergePlanner.sketchesWanted(input0, cfg, rules.planning),
        MinHash.Size(cfg.minhashSize.value)
      )
    val input =
      input0
        .modify(_.bodies)
        .setTo(
          MergeState.bodies(bundle, input0, MergePlanner.bodiesWanted(input0, sk, resolutions, cfg, rules.planning))
        )
    (input, MergePlanner.plan(input, sk, resolutions, cfg, rules.planning))
