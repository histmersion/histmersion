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
  def databaseSize: (String, ByteCount)

  /** The `n` largest registered tables, largest first (by name among equals). */
  def largestTables(n: Int): List[TableSize]

  /** How much each owner's tables hold, indexes and TOAST included, in the registry's order. */
  def ownerTables: List[(DataOwner, ByteCount)]

object DiagnosticsService:
  def apply(registry: DataContributions, transactor: Transactor): DiagnosticsService =
    Implementation(registry, transactor)

  /** The tables of `registry` in `sized` (every table by size, largest first), in that order. */
  private def registered(registry: DataContributions, sized: List[(String, Long)]): List[(String, Long)] =
    val names: Set[String] = registry.tables.map(t => t._2.name.value: String).toSet
    sized.filter((name, _) => names.contains(name))

  private def perOwner(registry: DataContributions, sized: List[(String, Long)]): List[(DataOwner, ByteCount)] =
    val bytes = sized.toMap
    registry.owners.map(o =>
      o -> ByteCount.applyUnsafe(
        registry.tables.collect { case (`o`, t) => bytes.getOrElse(t.name.value, 0L) }.sum
      )
    )

  final private class Implementation(registry: DataContributions, transactor: Transactor) extends DiagnosticsService:
    def figures(owner: DataOwner): DataFigures =
      registry.all.find(_.owner == owner).fold(DataFigures.empty)(c => transactor.read(c.figures))

    def databaseSize: (String, ByteCount) =
      val (name, bytes) = transactor.read(StatsRepo.databaseSize)
      (name, ByteCount.applyUnsafe(bytes))

    def largestTables(n: Int): List[TableSize] =
      if n <= 0 then Nil
      else registered(registry, transactor.read(StatsRepo.tableBytes)).take(n).map(TableSize(_, _))

    def ownerTables: List[(DataOwner, ByteCount)] = perOwner(registry, transactor.read(StatsRepo.tableBytes))

  /** Figures given up front: `counts` by owner, the database `database` with `tables` (largest first). */
  final class InMemory(
      registry: DataContributions,
      counts: Map[DataOwner, DataFigures],
      database: (String, ByteCount),
      tables: List[(TableName, ByteCount)]
  ) extends DiagnosticsService:
    private def sized = tables.map((t, b) => t.value -> b.value)
    def figures(owner: DataOwner): DataFigures = counts.getOrElse(owner, DataFigures.empty)
    def databaseSize: (String, ByteCount) = database
    def largestTables(n: Int): List[TableSize] =
      if n <= 0 then Nil else registered(registry, sized).take(n).map(TableSize(_, _))
    def ownerTables: List[(DataOwner, ByteCount)] = perOwner(registry, sized)
