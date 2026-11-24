package com.histmersion
package util.data.diagnostics

import com.histmersion.api.TableSize
import com.histmersion.util.data.{DataContributions, DataOwner, TableName}
import com.histmersion.util.database.Transactor
import com.histmersion.ByteCount
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** The figures of the 360° view, read from the registry: each owner's counts, the database's size, its largest
  * registered tables and how much each owner's tables hold.
  */
trait DiagnosticsService:
  /** The counts `owner` registers. */
  def figures(owner: DataOwner): DataFigures

  /** The database's name and its size on disk. */
  def databaseSize: (DiagnosticsService.DatabaseName, ByteCount)

  /** The `n` largest registered tables, largest first (by name among equals). */
  def largestTables(n: DiagnosticsService.TableLimit): List[TableSize]

  /** How much each owner's tables hold, indexes and TOAST included, in the registry's order. */
  def ownerTables: List[(DataOwner, ByteCount)]

object DiagnosticsService:
  type DatabaseName = DatabaseName.T
  object DatabaseName extends Newtype[String, Pure]
  type TableLimit = TableLimit.T
  object TableLimit extends Newtype[Int, Pure]

  def apply(registry: DataContributions, transactor: Transactor): DiagnosticsService =
    Implementation(registry, transactor)

  /** The tables of `registry` in `sized` (every table by size, largest first), in that order. */
  private def registered(
      registry: DataContributions,
      sized: List[(TableName, ByteCount)]
  ): List[(TableName, ByteCount)] =
    val names: Set[TableName] = registry.tables.map(t => t._2.name).toSet
    sized.filter((name, _) => names.contains(name))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def perOwner(registry: DataContributions, sized: List[(TableName, ByteCount)]): List[(DataOwner, ByteCount)] =
    val bytes = sized.toMap
    registry.owners.map(o =>
      o -> ByteCount.applyUnsafe(
        registry.tables.collect { case (`o`, t) => bytes.get(t.name).fold(0L)(b => b.value: Long) }.sum
      )
    )

  final private class Implementation(registry: DataContributions, transactor: Transactor) extends DiagnosticsService:
    def figures(owner: DataOwner): DataFigures =
      registry.all.find(_.owner == owner).fold(DataFigures.empty)(c => transactor.read(c.figures))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def databaseSize: (DatabaseName, ByteCount) =
      val (name, bytes) = transactor.read(StatsRepo.databaseSize)
      (DatabaseName(name.value), bytes)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    def largestTables(n: TableLimit): List[TableSize] =
      if n.value <= 0 then Nil
      else
        registered(registry, transactor.read(StatsRepo.tableBytes))
          .take(n.value)
          .map((name, bytes) => TableSize(TableSize.Name(name.value), com.histmersion.api.ByteCount(bytes.value)))

    def ownerTables: List[(DataOwner, ByteCount)] = perOwner(registry, transactor.read(StatsRepo.tableBytes))

  /** Figures given up front: `counts` by owner, the database `database` with `tables` (largest first). */
  final class InMemory(
      registry: DataContributions,
      counts: Map[DataOwner, DataFigures],
      database: (DatabaseName, ByteCount),
      tables: List[(TableName, ByteCount)]
  ) extends DiagnosticsService:
    private def sized = tables
    def figures(owner: DataOwner): DataFigures = counts.getOrElse(owner, DataFigures.empty)
    def databaseSize: (DatabaseName, ByteCount) = database
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    def largestTables(n: TableLimit): List[TableSize] =
      if n.value <= 0 then Nil
      else
        registered(registry, sized)
          .take(n.value)
          .map((name, bytes) => TableSize(TableSize.Name(name.value), com.histmersion.api.ByteCount(bytes.value)))
    def ownerTables: List[(DataOwner, ByteCount)] = perOwner(registry, sized)
