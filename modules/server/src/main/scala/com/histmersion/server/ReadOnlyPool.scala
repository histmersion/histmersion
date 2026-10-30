package com.histmersion.server

import com.zaxxer.hikari.HikariDataSource
import ox.{forkDiscard, useInScope}

import javax.sql.DataSource
import scala.concurrent.duration.*

/** The read-only role's pool: its own, or — when that role cannot connect — the application pool, which this node then
  * does not own.
  */
final case class ReadOnlyPool(ds: DataSource, own: Option[HikariDataSource])
