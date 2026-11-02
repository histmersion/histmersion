package com.histmersion.util.identity

import com.histmersion.api.given
import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.exchange.{BundleEntry, CarriedKind, ImportRank, ImportStage, RowsIn}

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.exchange.Bundle

/** What a bundle carries of the identity area: the tombstones, written before every other row (a row the other side
  * deleted makes room for one it created in its place).
  */
object TombstonesCarried:
  val tombstones: CarriedKind =
    CarriedKind.owned[Bundle.Tombstone](
      DataOwner("util.identity"),
      BundleEntry("entities/tombstones.ndjson"),
      List(RowsIn(ImportStage.Rows, ImportRank(0), _.tombstones()))
    )(scope => TombstoneBundleRepo.tombstones(scope).toList)(
      t => Some(t.deletedAt),
      (es, ls) => es.modify(_.tombstones).setTo(ls)
    )
