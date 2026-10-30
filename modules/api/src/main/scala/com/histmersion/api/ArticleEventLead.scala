package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A catalogue event an ARTICLE's period and region hold that its text never names — a lead listed on the article's
  * coverage, never inserted into its prose.
  */
final case class ArticleEventLead(
    eventId: String,
    label: String,
    kind: String,
    years: String,
    size: Option[String],
    findingsNear: Int,
    provider: String
) derives KindlingsCodecAsObject,
      KindlingsSchema
object ArticleEventLead:
  given Schema[ArticleEventLead] = summon[KindlingsSchema[ArticleEventLead]].schema

  /** Pure: does an article body name the event (its label or another of its names, ignoring case)? */
  def mentioned(body: String, names: List[String]): Boolean =
    val b = body.toLowerCase(java.util.Locale.ROOT)
    names.map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.length >= 4).exists(b.contains)

  /** Pure: the coverage section listing them. */
  def markdown(leads: List[ArticleEventLead]): String =
    if leads.isEmpty then ""
    else
      ("### Catalogue events in this article's period and region that it never names" :: "" ::
        leads.map { l =>
          s"- ${l.label} (`${l.eventId}`, ${l.kind}${l.size.fold("")(s => s", $s")}, ${l.years}) — " +
            s"${Plural.count(l.findingsNear, "finding")} of the filter's sources near it; from ${l.provider} (a lead, not evidence)"
        }).mkString("\n")
