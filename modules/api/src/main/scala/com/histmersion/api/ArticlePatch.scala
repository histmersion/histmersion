package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec

final case class ArticlePatch(
    title: Option[String] = None,
    summary: Option[String] = None,
    body: Option[String] = None,
    append: Option[String] = None,
    section: Option[String] = None,
    tags: Option[ListPatch] = None,
    regions: Option[List[String]] = None,
    period: Option[PeriodInput] = None,
    kind: Option[String] = None,
    ifUpdatedAt: Option[String] = None,
    filter: Option[ArticleFilter] = None,
    reasons: List[CoverageReason] = Nil,
    critiqueReasons: List[CritiqueReason] = Nil
) derives KindlingsSchema:
  def onlyCritiques: Boolean =
    critiqueReasons.nonEmpty && copy(critiqueReasons = Nil, ifUpdatedAt = None) == ArticlePatch()
object ArticlePatch:
  given Schema[ArticlePatch] = summon[KindlingsSchema[ArticlePatch]].schema

  given Codec.AsObject[ArticlePatch] = StrictJson.codec(KindlingsCodecAsObject.derived[ArticlePatch])
