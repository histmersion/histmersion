package com.histmersion
package util.data.maintenance

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Which part of a resumable piece of upkeep to run: the records after `after`, at most `limit` of them. */
final case class MaintenanceSlice(after: Option[MaintenanceSlice.After], limit: Option[MaintenanceSlice.Limit])

object MaintenanceSlice:
  type After = After.T
  object After extends RefinedType[String, Pure]
  type Limit = Limit.T
  object Limit extends RefinedType[Int, Pure]
