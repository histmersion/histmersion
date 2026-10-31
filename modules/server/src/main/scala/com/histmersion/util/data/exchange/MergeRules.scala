package com.histmersion.util.data.exchange

import com.histmersion.util.data.DataOwner

/** How a merge between two collections matches, folds and decides one kind of record. */
trait MergeRules:
  def owner: DataOwner
