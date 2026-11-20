package com.histmersion
package util.data.checks

import com.histmersion.util.data.checks.GuardedWrite.{ConcurrencyToken, FileText, RecordRef}
import com.histmersion.util.database.WritePermit
import io.github.iltotore.iron.constraint.any.Pure

/** A fix's write of one record's text through the record's own patch, guarded by the version token it was read with:
  * `false` when a concurrent writer moved the record on first, and the next check lists it again. Made under the permit
  * of the fix that writes it.
  */
final class GuardedWrite(write: (RecordRef, FileText, Option[ConcurrencyToken]) => WritePermit ?=> Boolean):
  def apply(record: RecordRef, text: FileText, token: Option[ConcurrencyToken])(using WritePermit): Boolean =
    write(record, text, token)

object GuardedWrite:
  type ConcurrencyToken = ConcurrencyToken.T
  object ConcurrencyToken extends Newtype[String, Pure]
  type FileText = FileText.T
  object FileText extends Newtype[String, Pure]
  type RecordRef = RecordRef.T
  object RecordRef extends Newtype[String, Pure]
