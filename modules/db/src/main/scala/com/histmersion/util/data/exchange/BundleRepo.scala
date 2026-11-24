package com.histmersion
package util.data.exchange
import com.histmersion.util.database.RangeSql

import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import java.util.UUID
import com.histmersion.util.data.exchange.BundleScope
import com.histmersion.util.data.exchange.BundleSql.*
import com.histmersion.util.storage.ContentStore
import com.histmersion.util.database.given
import com.histmersion.api.aliases.InstanceInfo
import com.histmersion.util.data.exchange.Bundle
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.database.Timestamps
import com.histmersion.util.database.Unused
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
@atBoundary(BoundaryKind.Sql, "reads and writes the bundle bookkeeping rows")
given DbCodec[InstanceInfo] = DbCodec.derived

/** The bundle's view of this collection itself (docs/research-merge-import.md §4): the instance, the fingerprint of the
  * reference data and the schema version. The owners read their own lines (`<Kind>BundleRepo`).
  */
object BundleRepo:

  // ── this instance ──────────────────────────────────────────────────────────────────────────

  def instance(using DbCon): InstanceInfo =
    sql"SELECT id, label, ${Timestamps.iso(sql"created_at")} FROM instance".query[InstanceInfo].run().head

  /** Name this instance; a blank label clears it. */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def setLabel(label: Option[InstanceInfo.Label])(using DbCon): InstanceInfo =
    val l = label.map(_.value.trim).filter(_.nonEmpty)
    Unused(sql"UPDATE instance SET label = $l".update.run(), Unused.Why.Held)
    instance

  // ── reads, one per file ────────────────────────────────────────────────────────────────────

  /** The gazetteer and the named periods, fingerprinted: not carried, compared ([[BundleReference]]). */
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  private[histmersion] def reference(using DbCon): com.histmersion.api.exchange.Bundle.Reference =
    // the SEED rows only: a place or period of this collection's own travels in the bundle itself
    def digest(table: Frag): (Int, String) =
      sql"""SELECT count(*)::int, coalesce(md5(string_agg(id, ',' ORDER BY id COLLATE "C")), '')
                  FROM $table WHERE dataset = 'seed'"""
        .query[(Int, String)]
        .run()
        .head
    val (np, dp) = digest(sql"places")
    val (nq, dq) = digest(sql"periods")
    com.histmersion.api.exchange.Bundle.Reference(
      com.histmersion.api.exchange.Bundle.Reference.Places(np),
      com.histmersion.api.exchange.Bundle.Reference.PlacesDigest(dp),
      com.histmersion.api.exchange.Bundle.Reference.Periods(nq),
      com.histmersion.api.exchange.Bundle.Reference.PeriodsDigest(dq)
    )

  // ── the schema ─────────────────────────────────────────────────────────────────────────────

  /** The Flyway version of this database, read in the caller's transaction ("33"). */
  @tolerated(LintRule.TotalOrder, "flyway_schema_history: installed_rank is its key (Flyway's table, not in the migrations)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  private[histmersion] def schemaVersion(using
      DbCon
  ): Option[com.histmersion.api.exchange.Bundle.Manifest.SchemaVersion] =
    sql"""SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL
          ORDER BY installed_rank DESC LIMIT 1"""
      .query[String]
      .run()
      .headOption
      .map(v => com.histmersion.api.exchange.Bundle.Manifest.SchemaVersion(v))
