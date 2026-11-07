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
  def databaseSize: (com.histmersion.util.data.diagnostics.DiagnosticsService.Problem, ByteCount)

  /** The `n` largest registered tables, largest first (by name among equals). */
  def largestTables(n: com.histmersion.util.data.diagnostics.DiagnosticsService.TableLimit): List[TableSize]

  /** How much each owner's tables hold, indexes and TOAST included, in the registry's order. */
  def ownerTables: List[(DataOwner, ByteCount)]

object DiagnosticsService:
  type DatabaseName = DatabaseName.T
  object DatabaseName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TableLimit = TableLimit.T
  object TableLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def apply(registry: DataContributions, transactor: Transactor): DiagnosticsService =
    Implementation(registry, transactor)

  /** The tables of `registry` in `sized` (every table by size, largest first), in that order. */
  private def registered(registry: DataContributions, sized: List[(com.histmersion.util.data.TableName, com.histmersion.ByteCount)]): List[(com.histmersion.util.data.diagnostics.DiagnosticsService.Problem, com.histmersion.ByteCount)] =
    val names: Set[com.histmersion.util.data.TableName] = registry.tables.map(t => t._2.name.value: String).toSet
    sized.filter((name, _) => names.contains(name))

  private def perOwner(registry: DataContributions, sized: List[(com.histmersion.util.data.DataOwner, com.histmersion.ByteCount)]): List[(DataOwner, ByteCount)] =
    val bytes = sized.toMap
    registry.owners.map(o =>
      o -> ByteCount.applyUnsafe(
        registry.tables.collect { case (`o`, t) => bytes.getOrElse(t.name.value, 0L) }.sum
      )
    )

  final private class Implementation(registry: DataContributions, transactor: Transactor) extends DiagnosticsService:
    def figures(owner: DataOwner): DataFigures =
      registry.all.find(_.owner == owner).fold(DataFigures.empty)(c => transactor.read(c.figures))

    def databaseSize: (com.histmersion.util.data.diagnostics.DiagnosticsService.Problem, ByteCount) =
      val (name, bytes) = transactor.read(StatsRepo.databaseSize)
      (name, ByteCount.applyUnsafe(bytes))

    def largestTables(n: com.histmersion.util.data.diagnostics.DiagnosticsService.TableLimit): List[TableSize] =
      if n <= 0 then Nil
      else registered(registry, transactor.read(StatsRepo.tableBytes)).take(n).map(TableSize(_, _))

    def ownerTables: List[(DataOwner, ByteCount)] = perOwner(registry, transactor.read(StatsRepo.tableBytes))


  /** Figures given up front: `counts` by owner, the database `database` with `tables` (largest first). */
  final class InMemory(
      registry: DataContributions,
      counts: Map[DataOwner, DataFigures],
      database: (InMemory.DatabasePart1, ByteCount),
      tables: List[(TableName, ByteCount)]
  ) extends DiagnosticsService:
    private def sized = tables.map((t, b) => t.value -> b.value)
    def figures(owner: DataOwner): DataFigures = counts.getOrElse(owner, DataFigures.empty)
    def databaseSize: (com.histmersion.util.data.diagnostics.DiagnosticsService.Problem, ByteCount) = database
    def largestTables(n: com.histmersion.util.data.diagnostics.DiagnosticsService.TableLimit): List[TableSize] =
      if n <= 0 then Nil else registered(registry, sized).take(n).map(TableSize(_, _))
    def ownerTables: List[(DataOwner, ByteCount)] = perOwner(registry, sized)

  object InMemory:
    type DatabasePart1 = com.histmersion.util.data.diagnostics.DiagnosticsService.DatabaseName
    val DatabasePart1: _root_.com.histmersion.util.data.diagnostics.DiagnosticsService.DatabaseName.type = _root_.com.histmersion.util.data.diagnostics.DiagnosticsService.DatabaseName

