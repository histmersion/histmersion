package com.histmersion.api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec
import com.histmersion.api.StrictJson
import com.histmersion.api.given

final case class GoalPut(
    title: String,
    status: String = "planned",
    notes: Option[String] = None,
    tags: List[String] = Nil,
    regions: List[String] = Nil,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None,
    parentSlug: Option[String] = None
) derives KindlingsSchema

object GoalPut:
  given Schema[GoalPut] = summon[KindlingsSchema[GoalPut]].schema

  given Codec.AsObject[GoalPut] = StrictJson.codec(KindlingsCodecAsObject.derived[GoalPut])
