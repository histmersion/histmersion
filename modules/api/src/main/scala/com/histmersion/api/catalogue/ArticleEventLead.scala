package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.Plural
import com.histmersion.api.given

final case class ArticleEventLead(
    eventId: com.histmersion.api.catalogue.Event.Id,
    label: ArticleEventLead.Label,
    kind: ArticleEventLead.Kind,
    years: ArticleEventLead.Years,
    size: Option[ArticleEventLead.Size],
    findingsNear: ArticleEventLead.FindingsNear,
    provider: ArticleEventLead.Provider
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object ArticleEventLead:
  type Body = Body.T
  object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Markdown = Markdown.T
  object Markdown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]



  type Years = Years.T
  object Years extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Size = Size.T
  object Size extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindingsNear = FindingsNear.T
  object FindingsNear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def mentioned(body: com.histmersion.api.catalogue.ArticleEventLead.Body, names: List[com.histmersion.api.catalogue.EventEntry.Names]): Boolean =
    val b = (body).value.toLowerCase(java.util.Locale.ROOT)
    (names.map((_).value.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.length >= 4).exists(b.contains))

  def markdown(leads: List[ArticleEventLead]): com.histmersion.api.catalogue.ArticleEventLead.Markdown =
    if leads.isEmpty then _root_.com.histmersion.api.catalogue.ArticleEventLead.Markdown("")
    else
      ("### Catalogue events in this article's period and region that it never names" :: "" ::
        leads.map { l =>
          s"- ${l.label} (`${l.eventId}`, ${l.kind}${l.size.fold("")(s => s", $s")}, ${l.years}) — " +
            s"${Plural.count(l.findingsNear, _root_.com.histmersion.api.Plural.Noun.Singular("finding"))} of the filter's sources near it; from ${l.provider} (a lead, not evidence)"
        }).mkString("\n")
