package com.histmersion.util.identity

import com.histmersion.util.database.given
import com.histmersion.api.exchange.BundleTombstone
import com.histmersion.util.database.Sql
import com.histmersion.util.data.exchange.BundleScope
import com.histmersion.util.data.exchange.BundleSql.*
import com.histmersion.values.BundleKind

import com.augustnagro.magnum.*

/** What a bundle carries of the tombstones: the records deleted here, read as the bundle's lines by uid (the import
  * writes them first, `BundleImport.Writer.tombstones`, as only it knows which rows its plan deletes).
  */
object TombstoneBundleRepo:

  def tombstones(scope: BundleScope)(using DbCon): Vector[BundleTombstone] =
    (Sql.raw(s"SELECT uid, kind, origin, ${ts("deleted_at")} FROM tombstones WHERE ") ++
      where(scope, "uid", "deleted_at") ++ " ORDER BY uid").frag.query[BTombstoneRow].run().flatMap { r =>
      BundleKind.fromWire(r.kind).map(BundleTombstone(r.uid, _, r.origin, r.deletedAt))
    }
