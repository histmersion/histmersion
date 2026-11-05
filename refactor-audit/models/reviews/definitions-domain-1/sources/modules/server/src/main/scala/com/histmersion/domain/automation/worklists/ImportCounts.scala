package com.histmersion.domain.automation.worklists

/** What an import did: the rows it wrote and the rows it removed first. */
final case class ImportCounts(written: ImportCounts.Written, removed: ImportCounts.Removed)

object ImportCounts:
  type Written = Written.T
  object Written extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Removed = Removed.T
  object Removed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
