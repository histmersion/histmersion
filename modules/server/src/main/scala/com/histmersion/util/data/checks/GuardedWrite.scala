package com.histmersion.util.data.checks

/** A fix's write of one record's text through the record's own patch, guarded by the version token it was read with:
  * `false` when a concurrent writer moved the record on first, and the next check lists it again.
  */
trait GuardedWrite:
  def apply(record: com.histmersion.util.data.checks.GuardedWrite.RecordRef, text: com.histmersion.util.data.checks.GuardedWrite.FileText, token: Option[com.histmersion.util.data.checks.GuardedWrite.ConcurrencyToken]): Boolean

object GuardedWrite:
  type ConcurrencyToken = ConcurrencyToken.T
  object ConcurrencyToken extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FileText = FileText.T
  object FileText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordRef = RecordRef.T
  object RecordRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
