package com.histmersion.domain.media.imports

import java.time.Instant

/** That a provider was asked about a place, an event or a source, as the in-memory store holds it (`media_checks`). */
final case class HeldCheck(
    provider: HeldCheck.Provider,
    targetType: HeldCheck.TargetType,
    targetId: HeldCheck.TargetId,
    found: HeldCheck.Found,
    qid: Option[HeldCheck.Qid],
    checkedAt: Instant
)

object HeldCheck:
  type Provider = Provider.T
  object Provider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetType = TargetType.T
  object TargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetId = TargetId.T
  object TargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Found = Found.T
  object Found extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Qid = Qid.T
  object Qid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
