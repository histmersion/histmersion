package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID

final case class MergeResolutions(
    resolve: List[MergeResolution] = Nil,
    policy: MergePolicy = MergePolicy(),
    bundle: Option[UUID] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def overriddenBy(later: MergeResolutions): MergeResolutions =
    val ids = later.resolve.map(_.issue).toSet
    MergeResolutions(
      resolve.filterNot(r => ids(r.issue)) ++ later.resolve,
      MergePolicy(later.policy.editions.orElse(policy.editions)),
      later.bundle.orElse(bundle)
    )
object MergeResolutions:
  given Schema[MergeResolutions] = summon[KindlingsSchema[MergeResolutions]].schema
