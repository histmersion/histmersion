package com.histmersion.util.database

import com.histmersion.util.data.{DataContribution, DataOwner, OwnedTable}
import com.histmersion.util.data.diagnostics.DataFigures
import com.histmersion.util.storage.Location
import com.augustnagro.magnum.DbCon

/** What the database area says about what it keeps: Flyway's history, PostGIS's reference systems and the database
  * cluster's own directory.
  */
object DatabaseData extends DataContribution:
  val owner: DataOwner = DataOwner("util.database")

  val tables: List[OwnedTable] = OwnedTable.all("flyway_schema_history", "spatial_ref_sys")

  override val stores: List[Location] = List(Location.DatabaseCluster)

  def figures(using DbCon): DataFigures = DataFigures.empty
