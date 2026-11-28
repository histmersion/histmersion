package com.histmersion
package api.automation.approvaldigest

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.Approval
import io.github.iltotore.iron.constraint.any.Pure

final case class KindGroup(
    kind: KindGroup.Kind,
    count: KindGroup.Count,
    oldest: Option[KindGroup.Oldest] = None,
    decidedBy: KindGroup.DecidedBy = Approval.DecidedBy.Wire("person"),
    subjects: List[SubjectCount] = Nil,
    ids: List[Open.Id] = Nil
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object KindGroup:
  export Approval.Kind.Wire as Kind
  type Count = Count.T
  object Count extends Newtype[Int, Pure]
  type Oldest = Oldest.T
  object Oldest extends Newtype[String, Pure]
  export Approval.DecidedBy.Wire as DecidedBy
