package com.histmersion
package util.storage

import com.histmersion.ByteCount

/** What a prune removed. */
final case class PruneReport(removed: List[Stored]):
  def freed: ByteCount = ByteCount.applyUnsafe(removed.iterator.map(_.size.value).sum)
