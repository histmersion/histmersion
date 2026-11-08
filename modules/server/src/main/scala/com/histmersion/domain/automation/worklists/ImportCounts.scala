package com.histmersion
package domain.automation.worklists

import io.github.iltotore.iron.constraint.any.Pure

/** What an import did: the rows it wrote and the rows it removed first. */
final case class ImportCounts(written: ImportCounts.Written, removed: ImportCounts.Removed)

object ImportCounts:
  type Written = Written.T
  object Written extends Newtype[Int, Pure]
  type Removed = Removed.T
  object Removed extends Newtype[Int, Pure]
