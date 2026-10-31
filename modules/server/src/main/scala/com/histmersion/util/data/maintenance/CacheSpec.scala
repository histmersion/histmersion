package com.histmersion.util.data.maintenance

import com.histmersion.util.data.DataOwner

/** What an owner keeps derived from its rows. */
trait CacheSpec:
  def owner: DataOwner
