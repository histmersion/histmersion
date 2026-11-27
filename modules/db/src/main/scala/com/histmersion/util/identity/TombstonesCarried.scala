package com.histmersion
package util.identity

import com.histmersion.api.given
import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.exchange.{BundleEntry, CarriedKind, ImportRank, ImportStage, RowsIn}

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.exchange.Bundle
import com.histmersion.lint.{tolerated, LintRule}

/** What a bundle carries of the identity area: the tombstones, written before every other row (a row the other side
  * deleted makes room for one it created in its place).
  */
object TombstonesCarried:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val tombstones: CarriedKind =
    CarriedKind.owned[Bundle.Tombstone](
      DataOwner("util.identity"),
      BundleEntry("entities/tombstones.ndjson"),
      List(RowsIn(ImportStage.Rows, ImportRank(0), _.tombstones()))
    )(scope => TombstoneBundleRepo.tombstones(scope).toList)(
      t => Some(CarriedKind.OwnedClock(t.deletedAt.value)),
      (es, ls) => es.modify(_.tombstones).setTo(ls)
    )
