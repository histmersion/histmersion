package com.histmersion
package util.log

import com.augustnagro.magnum.DbCon
import com.histmersion.util.data.{DataContribution, DataOwner, OwnedTable, TableName}
import com.histmersion.util.data.backup.BackupShare
import com.histmersion.util.data.diagnostics.DataFigures

/** What the structured log keeps: its events, the write statements waiting for the writer and the per-minute counts of
  * the counted ones. A debugging aid, not research: a backup leaves its rows out, and no bundle carries it.
  */
object LogData extends DataContribution:
  val owner: DataOwner = DataOwner("util.log")

  val tables: List[OwnedTable] = OwnedTable.all("log_events", "log_writes", "log_counts")

  override val backup: BackupShare = BackupShare(rowsLeftOut = tables.map(_.name))

  def figures(using DbCon): DataFigures = DataFigures.empty

  /** The tables whose writes the database neither records nor counts: the log's own, the bulk text chunks, the caches
    * and what PostGIS and Flyway own.
    */
  val unrecorded: List[TableName] = List(
    TableName("log_events"),
    TableName("log_writes"),
    TableName("log_counts"),
    TableName("source_chunks"),
    TableName("map_changes"),
    TableName("site_undated_cells"),
    TableName("site_undated_build"),
    TableName("flyway_schema_history"),
    TableName("spatial_ref_sys")
  )

  /** The tables whose writes are counted per minute instead of recorded one by one: the agent lease's renewals and the
    * quota updates. (The jobs' heartbeats are counted too, by the statement, not the table.)
    */
  val counted: List[TableName] =
    List(
      TableName("agent_leases"),
      TableName("upstream_quota"),
      TableName("plan_quota_readings"),
      TableName("plan_quota_refreshes")
    )
