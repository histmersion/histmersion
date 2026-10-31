package com.histmersion.util.data.maintenance

import com.zaxxer.hikari.HikariDataSource

import javax.sql.DataSource

/** The read-only role's pool: its own, or — when that role cannot connect — the application pool, which this node then
  * does not own.
  */
final case class ReadOnlyPool(ds: DataSource, own: Option[HikariDataSource])
