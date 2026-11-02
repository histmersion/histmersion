package com.histmersion.util.data

import com.histmersion.util.data.backup.{BackupAdapter, BackupShare}
import com.histmersion.util.data.diagnostics.{DataFigures, FiguresAdapter}
import com.histmersion.util.data.exchange.{CarriedKind, CarriedKindsAdapter, MergeRules, MergeRulesAdapter}
import com.histmersion.util.data.maintenance.{CacheSpec, MaintenanceAdapter, MaintenanceTask}
import com.histmersion.util.storage.Location
import com.augustnagro.magnum.DbCon

/** Today's per-kind code registered as each owner's contribution, until the owner's own slice registers it from its
  * package: the tables, stores, carried kinds, merge rules, caches, upkeep and figures the adapters list for it.
  */
object TransitionContributions:

  final private class Adapted(val owner: DataOwner, ownTasks: List[MaintenanceTask]) extends DataContribution:
    val tables: List[OwnedTable] = TablesAdapter.of(owner)
    override val stores: List[Location] = MaintenanceAdapter.stores(owner)
    override val backup: BackupShare = BackupAdapter.of(owner)
    override val carried: List[CarriedKind] = CarriedKindsAdapter.of(owner)
    override val mergeRules: List[MergeRules] = MergeRulesAdapter.of(owner)
    override val caches: List[CacheSpec] = MaintenanceAdapter.caches(owner)
    override val maintenance: List[MaintenanceTask] = MaintenanceAdapter.tasks(owner) ++ ownTasks
    def figures(using DbCon): DataFigures = FiguresAdapter.of(owner)

  val all: List[DataContribution] = withTasks(Nil)

  /** The same, with the upkeep an owner's adapter registers from the owner's own place (it names that owner's
    * repositories, which code here may not), handed in by the composition root.
    */
  def withTasks(tasks: List[MaintenanceTask]): List[DataContribution] = withOwn(Nil, tasks)

  /** The same, with the contributions the owners that register their own hand in (the catalogue's), each in its owner's
    * place in the order every operation runs the owners in.
    */
  def withOwn(own: List[DataContribution], tasks: List[MaintenanceTask]): List[DataContribution] =
    TablesAdapter.owners.map(o => own.find(_.owner == o).getOrElse(Adapted(o, tasks.filter(_.owner == o))))
