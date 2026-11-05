package com.histmersion.util.data.maintenance

/** Which part of a resumable piece of upkeep to run: the records after `after`, at most `limit` of them. */
final case class MaintenanceSlice(after: Option[String], limit: Option[Int])
