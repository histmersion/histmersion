package com.histmersion.util.data.checks

/** A fix's write of one record's text through the record's own patch, guarded by the version token it was read with:
  * `false` when a concurrent writer moved the record on first, and the next check lists it again.
  */
trait GuardedWrite:
  def apply(record: String, text: String, token: Option[String]): Boolean
