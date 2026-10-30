package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec

final case class GoalPatch(
    title: Option[String] = None,
    status: Option[String] = None,
    notes: Option[String] = None,
    tags: Option[List[String]] = None,
    regions: Option[List[String]] = None,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None,
    parentSlug: Option[String] = None,
    budgetUsd: Option[Double] = None
) derives KindlingsSchema

object GoalPatch:
  given Schema[GoalPatch] = summon[KindlingsSchema[GoalPatch]].schema

  given Codec.AsObject[GoalPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[GoalPatch])
