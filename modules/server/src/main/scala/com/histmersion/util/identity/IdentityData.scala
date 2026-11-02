package com.histmersion.util.identity

import com.histmersion.util.data.{DataContribution, DataOwner, OwnedTable}
import com.histmersion.util.data.diagnostics.DataFigures
import com.histmersion.util.data.exchange.CarriedKind
import com.augustnagro.magnum.DbCon

/** What the identity area says about the data it owns: the instance, the uids' aliases and the tombstones, of which a
  * bundle carries the tombstones.
  */
object IdentityData extends DataContribution:
  val owner: DataOwner = DataOwner("util.identity")

  val tables: List[OwnedTable] = OwnedTable.all("instance", "uid_aliases", "tombstones", "alias_tombstones")

  override val carried: List[CarriedKind] = List(TombstonesCarried.tombstones)

  def figures(using DbCon): DataFigures = DataFigures.empty
