package com.histmersion
package domain.media.imports

import java.time.Instant
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
  object Provider extends RefinedType[String, Pure]
  type TargetType = TargetType.T
  object TargetType extends RefinedType[String, Pure]
  type TargetId = TargetId.T
  object TargetId extends RefinedType[String, Pure]
  type Found = Found.T
  object Found extends RefinedType[Int, Pure]
  type Qid = Qid.T
  object Qid extends RefinedType[String, Pure]
