package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec

/** Selective enrichment. Every part is independently optional; absent parts are untouched. */
final case class ArticlePatch(
    title: Option[String] = None,
    summary: Option[String] = None,
    body: Option[String] = None,
    /** Markdown appended after the current body — how to EXTEND an article without re-uploading it. */
    append: Option[String] = None,
    /** When given with `append`, the append text is inserted under this heading (e.g. `"## Disagreements"`) instead of
      * at the end of the body.
      */
    section: Option[String] = None,
    tags: Option[ListPatch] = None,
    regions: Option[List[String]] = None,
    period: Option[PeriodInput] = None,
    kind: Option[String] = None,
    ifUpdatedAt: Option[String] = None,
    /** Replace the filter the article is compiled from (#958); an empty filter `{}` clears it. */
    filter: Option[ArticleFilter] = None,
    /** Set the reason of each named source (an empty reason removes it); the others are kept. */
    reasons: List[CoverageReason] = Nil,
    /** Set why a disagreement of the critique checklist does not bear on the article (V091), per pair (an empty reason
      * removes it); the others are kept. Stored beside the article: a patch of these alone writes no version and leaves
      * `updatedAt` as it is.
      */
    critiqueReasons: List[CritiqueReason] = Nil
) derives KindlingsSchema:
  /** Nothing but critique reasons (and the token): the article row is not written. */
  def onlyCritiques: Boolean =
    critiqueReasons.nonEmpty && copy(critiqueReasons = Nil, ifUpdatedAt = None) == ArticlePatch()
object ArticlePatch:
  given Schema[ArticlePatch] = summon[KindlingsSchema[ArticlePatch]].schema

  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[ArticlePatch] = StrictJson.codec(KindlingsCodecAsObject.derived[ArticlePatch])
