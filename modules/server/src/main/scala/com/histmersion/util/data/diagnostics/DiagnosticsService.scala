package com.histmersion.util.data.diagnostics

import com.histmersion.api.TableSize
import com.histmersion.util.data.{DataContributions, DataOwner, TableName}
import com.histmersion.util.database.Transactor
import com.histmersion.ByteCount

/** The figures of the 360° view, read from the registry: each owner's counts, the database's size, its largest
  * registered tables and how much each owner's tables hold.
  */
trait DiagnosticsService:
  /** The counts `owner` registers. */
  def figures(owner: DataOwner): DataFigures

  /** The database's name and its size on disk. */
  def databaseSize: (com.histmersion.util.data.diagnostics.DiagnosticsService.DatabaseSizeResultPart1, ByteCount)

  /** The `n` largest registered tables, largest first (by name among equals). */
  def largestTables(n: com.histmersion.util.data.diagnostics.DiagnosticsService.LargestTablesN): List[TableSize]

  /** How much each owner's tables hold, indexes and TOAST included, in the registry's order. */
  def ownerTables: List[(DataOwner, ByteCount)]

object DiagnosticsService:
  type DatabaseSizeResultPart1 = DatabaseSizeResultPart1.T
  object DatabaseSizeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LargestTablesN = LargestTablesN.T
  object LargestTablesN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegisteredResultPart1 = RegisteredResultPart1.T
  object RegisteredResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegisteredResultPart2 = RegisteredResultPart2.T
  object RegisteredResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegisteredSizedPart1 = RegisteredSizedPart1.T
  object RegisteredSizedPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegisteredSizedPart2 = RegisteredSizedPart2.T
  object RegisteredSizedPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PerOwnerSizedPart1 = PerOwnerSizedPart1.T
  object PerOwnerSizedPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PerOwnerSizedPart2 = PerOwnerSizedPart2.T
  object PerOwnerSizedPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(registry: DataContributions, transactor: Transactor): DiagnosticsService =
    Implementation(registry, transactor)

  /** The tables of `registry` in `sized` (every table by size, largest first), in that order. */
  private def registered(registry: DataContributions, sized: List[(com.histmersion.util.data.diagnostics.DiagnosticsService.RegisteredSizedPart1, com.histmersion.util.data.diagnostics.DiagnosticsService.RegisteredSizedPart2)]): List[(com.histmersion.util.data.diagnostics.DiagnosticsService.RegisteredResultPart1, com.histmersion.util.data.diagnostics.DiagnosticsService.RegisteredResultPart2)] =
    val names: Set[String] = registry.tables.map(t => t._2.name.value: String).toSet
    sized.filter((name, _) => names.contains(name))

  private def perOwner(registry: DataContributions, sized: List[(com.histmersion.util.data.diagnostics.DiagnosticsService.PerOwnerSizedPart1, com.histmersion.util.data.diagnostics.DiagnosticsService.PerOwnerSizedPart2)]): List[(DataOwner, ByteCount)] =
    val bytes = sized.toMap
    registry.owners.map(o =>
      o -> ByteCount.applyUnsafe(
        registry.tables.collect { case (`o`, t) => bytes.getOrElse(t.name.value, 0L) }.sum
      )
    )

  final private class Implementation(registry: DataContributions, transactor: Transactor) extends DiagnosticsService:
    def figures(owner: DataOwner): DataFigures =
      registry.all.find(_.owner == owner).fold(DataFigures.empty)(c => transactor.read(c.figures))

    def databaseSize: (com.histmersion.util.data.diagnostics.DiagnosticsService.Implementation.DatabaseSizeResultPart1, ByteCount) =
      val (name, bytes) = transactor.read(StatsRepo.databaseSize)
      (name, ByteCount.applyUnsafe(bytes))

    def largestTables(n: com.histmersion.util.data.diagnostics.DiagnosticsService.Implementation.LargestTablesN): List[TableSize] =
      if n <= 0 then Nil
      else registered(registry, transactor.read(StatsRepo.tableBytes)).take(n).map(TableSize(_, _))

    def ownerTables: List[(DataOwner, ByteCount)] = perOwner(registry, transactor.read(StatsRepo.tableBytes))

  object Implementation:
    type DatabaseSizeResultPart1 = DatabaseSizeResultPart1.T
    object DatabaseSizeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LargestTablesN = LargestTablesN.T
    object LargestTablesN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Figures given up front: `counts` by owner, the database `database` with `tables` (largest first). */
  final class InMemory(
      registry: DataContributions,
      counts: Map[DataOwner, DataFigures],
      database: (String, ByteCount),
      tables: List[(TableName, ByteCount)]
  ) extends DiagnosticsService:
    private def sized = tables.map((t, b) => t.value -> b.value)
    def figures(owner: DataOwner): DataFigures = counts.getOrElse(owner, DataFigures.empty)
    def databaseSize: (com.histmersion.util.data.diagnostics.DiagnosticsService.InMemory.DatabaseSizeResultPart1, ByteCount) = database
    def largestTables(n: com.histmersion.util.data.diagnostics.DiagnosticsService.InMemory.LargestTablesN): List[TableSize] =
      if n <= 0 then Nil else registered(registry, sized).take(n).map(TableSize(_, _))
    def ownerTables: List[(DataOwner, ByteCount)] = perOwner(registry, sized)

  object InMemory:
    type DatabaseSizeResultPart1 = DatabaseSizeResultPart1.T
    object DatabaseSizeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LargestTablesN = LargestTablesN.T
    object LargestTablesN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
