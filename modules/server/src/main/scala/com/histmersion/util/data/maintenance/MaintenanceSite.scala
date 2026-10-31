package com.histmersion.util.data.maintenance

import com.histmersion.config.AppConfig

import javax.sql.DataSource

/** What a piece of upkeep runs against: the database and the settings the calling thread sees. */
final case class MaintenanceSite(dataSource: DataSource, settings: AppConfig)
