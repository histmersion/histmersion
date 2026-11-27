package com.histmersion
package util.identity

import com.histmersion.api.exchange.Bundle

import com.histmersion.util.database.given
import com.histmersion.util.data.exchange.BundleScope
import com.histmersion.util.data.exchange.BundleSql.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.Timestamps
import com.histmersion.lint.{tolerated, LintRule}

/** What a bundle carries of the tombstones: the records deleted here, read as the bundle's lines by uid (the import
  * writes them first, `BundleImport.Writer.tombstones`, as only it knows which rows its plan deletes).
  */
object TombstoneBundleRepo:

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def tombstones(scope: BundleScope)(using DbCon): Vector[Bundle.Tombstone] =
    sql"SELECT uid, kind, origin, ${Timestamps.iso(sql"deleted_at")} FROM tombstones WHERE ${where(scope, sql"uid", sql"deleted_at")} ORDER BY uid"
      .query[BTombstoneRow]
      .run()
      .flatMap { r =>
        Bundle.Kind
          .fromWire(Bundle.Kind.Wire(r.kind.value))
          .map(kind => Bundle.Tombstone(r.uid, kind, r.origin, Bundle.Tombstone.DeletedAt(r.deletedAt.value)))
      }
