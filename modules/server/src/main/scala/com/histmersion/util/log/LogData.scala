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
    "log_events",
    "log_writes",
    "log_counts",
    "source_chunks",
    "map_changes",
    "site_undated_cells",
    "site_undated_build",
    "flyway_schema_history",
    "spatial_ref_sys"
  ).map(TableName(_))

  /** The tables whose writes are counted per minute instead of recorded one by one: the agent lease's renewals and the
    * quota updates. (The jobs' heartbeats are counted too, by the statement, not the table.)
    */
  val counted: List[TableName] =
    List("agent_leases", "upstream_quota", "plan_quota_readings", "plan_quota_refreshes").map(TableName(_))
