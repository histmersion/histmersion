package com.histmersion
package api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.Plural
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class ArticleEventLead(
    eventId: Event.Id,
    label: ArticleEventLead.Label,
    kind: ArticleEventLead.Kind,
    years: ArticleEventLead.Years,
    size: Option[ArticleEventLead.Size],
    findingsNear: ArticleEventLead.FindingsNear,
    provider: ArticleEventLead.Provider
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema
object ArticleEventLead:
  type Body = Body.T
  object Body extends Newtype[String, Pure]
  type Markdown = Markdown.T
  object Markdown extends Newtype[String, Pure]

  export CatalogRecord.{Kind, Label}
  type Years = Years.T
  object Years extends Newtype[String, Pure]
  type Size = Size.T
  object Size extends Newtype[String, Pure]
  type FindingsNear = FindingsNear.T
  object FindingsNear extends Newtype[Int, Pure]
  export CatalogRecord.Provider

  def mentioned(body: Body, names: List[EventEntry.Names]): Boolean =
    val b = (body).value.toLowerCase(java.util.Locale.ROOT)
    (names.map((_).value.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.length >= 4).exists(b.contains))

  def markdown(leads: List[ArticleEventLead]): Markdown =
    if leads.isEmpty then Markdown("")
    else
      Markdown(
        ("### Catalogue events in this article's period and region that it never names" :: "" ::
          leads.map { l =>
            s"- ${l.label} (`${l.eventId}`, ${l.kind}${l.size.fold("")(s => s", $s")}, ${l.years}) — " +
              s"${Plural.count(Plural.CountN(l.findingsNear.value.toLong), Plural.Noun.Singular("finding"))} of the filter's sources near it; from ${l.provider} (a lead, not evidence)"
          }).mkString("\n")
      )
