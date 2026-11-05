package com.histmersion.util.data.maintenance

/** Which part of a resumable piece of upkeep to run: the records after `after`, at most `limit` of them. */
final case class MaintenanceSlice(after: Option[MaintenanceSlice.After], limit: Option[MaintenanceSlice.Limit])

object MaintenanceSlice:
  type After = After.T
  object After extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
