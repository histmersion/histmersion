package com.histmersion.util.data.checks

/** A fix's write of one record's text through the record's own patch, guarded by the version token it was read with:
  * `false` when a concurrent writer moved the record on first, and the next check lists it again.
  */
trait GuardedWrite:
  def apply(record: com.histmersion.util.data.checks.GuardedWrite.ApplyRecord, text: com.histmersion.util.data.checks.GuardedWrite.ApplyText, token: Option[com.histmersion.util.data.checks.GuardedWrite.ApplyToken]): com.histmersion.util.data.checks.GuardedWrite.ApplyResult

object GuardedWrite:
  type ApplyResult = ApplyResult.T
  object ApplyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyRecord = ApplyRecord.T
  object ApplyRecord extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyText = ApplyText.T
  object ApplyText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyToken = ApplyToken.T
  object ApplyToken extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
