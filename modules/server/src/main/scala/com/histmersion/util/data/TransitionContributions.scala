package com.histmersion.util.data

import com.histmersion.util.data.checks.{CheckRule, CheckRulesAdapter}
import com.histmersion.util.data.diagnostics.{DataFigures, FiguresAdapter}
import com.histmersion.util.data.exchange.{CarriedKind, CarriedKindsAdapter, MergeRules, MergeRulesAdapter}
import com.histmersion.util.data.mirror.{MirrorAdapter, MirrorExport}
import com.histmersion.util.data.maintenance.{CacheSpec, MaintenanceAdapter, MaintenanceTask}
import com.histmersion.util.storage.Location
import com.augustnagro.magnum.DbCon

/** Today's per-kind code registered as each owner's contribution, until the owner's own slice registers it from its
  * package: the tables, stores, carried kinds, merge rules, mirror notes, caches, upkeep, checks and figures the
  * adapters list for it.
  */
object TransitionContributions:

  final private class Adapted(val owner: DataOwner) extends DataContribution:
    val tables: List[OwnedTable] = TablesAdapter.of(owner)
    override val stores: List[Location] = MaintenanceAdapter.stores(owner)
    override val carried: List[CarriedKind] = CarriedKindsAdapter.of(owner)
    override val mergeRules: List[MergeRules] = MergeRulesAdapter.of(owner)
    override val mirror: List[MirrorExport] = MirrorAdapter.of(owner)
    override val caches: List[CacheSpec] = MaintenanceAdapter.caches(owner)
    override val maintenance: List[MaintenanceTask] = MaintenanceAdapter.tasks(owner)
    override val checks: List[CheckRule] = CheckRulesAdapter.of(owner)
    def figures(using DbCon): DataFigures = FiguresAdapter.of(owner)

  val all: List[DataContribution] = TablesAdapter.owners.map(Adapted(_))
