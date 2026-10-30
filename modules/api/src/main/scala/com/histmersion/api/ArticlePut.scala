package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec

// ── wire DTOs (Kindlings derives the circe codec AND the tapir schema from each) ────────────────

/** Full create/replace of one article. */
final case class ArticlePut(
    title: String,
    kind: String,
    body: String,
    summary: Option[String] = None,
    tags: List[String] = Nil,
    regions: List[String] = Nil,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None,
    /** The filter the article is compiled from (#958): its Not used list and Method note are generated. */
    filter: Option[ArticleFilter] = None,
    /** Why each source of the filter the body does not cite is left out. */
    reasons: List[CoverageReason] = Nil
) derives KindlingsSchema
object ArticlePut:
  given Schema[ArticlePut] = summon[KindlingsSchema[ArticlePut]].schema

  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
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
