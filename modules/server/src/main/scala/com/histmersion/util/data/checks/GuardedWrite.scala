package com.histmersion
package util.data.checks

import io.github.iltotore.iron.constraint.any.Pure

/** A fix's write of one record's text through the record's own patch, guarded by the version token it was read with:
  * `false` when a concurrent writer moved the record on first, and the next check lists it again.
  */
trait GuardedWrite:
  def apply(
      record: GuardedWrite.RecordRef,
      text: GuardedWrite.FileText,
      token: Option[GuardedWrite.ConcurrencyToken]
  ): Boolean

object GuardedWrite:
  type ConcurrencyToken = ConcurrencyToken.T
  object ConcurrencyToken extends Newtype[String, Pure]
  type FileText = FileText.T
  object FileText extends Newtype[String, Pure]
  type RecordRef = RecordRef.T
  object RecordRef extends Newtype[String, Pure]
