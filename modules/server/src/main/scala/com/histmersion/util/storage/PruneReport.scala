package com.histmersion
package util.storage

import com.histmersion.ByteCount
import com.histmersion.lint.{LintRule, tolerated}

/** What a prune removed. */
final case class PruneReport(removed: List[Stored]):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def freed: ByteCount = ByteCount.applyUnsafe(removed.iterator.map(_.size.value).sum)
