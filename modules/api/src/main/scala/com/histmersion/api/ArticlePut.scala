package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec

final case class ArticlePut(
    title: String,
    kind: String,
    body: String,
    summary: Option[String] = None,
    tags: List[String] = Nil,
    regions: List[String] = Nil,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None,
    filter: Option[ArticleFilter] = None,
    reasons: List[CoverageReason] = Nil
) derives KindlingsSchema
object ArticlePut:
  given Schema[ArticlePut] = summon[KindlingsSchema[ArticlePut]].schema

  given Codec.AsObject[ArticlePut] = StrictJson.codec(KindlingsCodecAsObject.derived[ArticlePut])

  def of(a: Article): ArticlePut = ArticlePut(
    a.title,
    a.kind,
    a.body,
    a.summary,
    a.tags,
    a.regions,
    a.period.map(_.start),
    a.period.map(_.end),
    a.filter,
    CoverageReason.list(a.reasons)
  )
