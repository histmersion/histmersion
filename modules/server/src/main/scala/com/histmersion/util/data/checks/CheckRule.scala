package com.histmersion.util.data.checks

import com.histmersion.util.data.DataOwner

/** One lint rule or health check of an owner's data. */
trait CheckRule:
  def owner: DataOwner
