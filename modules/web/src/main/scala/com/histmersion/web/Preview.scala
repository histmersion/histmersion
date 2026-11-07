package com.histmersion.web

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.YearFmt
import com.raquo.laminar.api.L.*
import com.histmersion.web.ui.Layout
import org.scalajs.dom
import org.scalajs.dom.{document, window}

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Failure, Success}
import com.histmersion.api.media.MediaRole
import com.histmersion.api.catalogue.{Corrections, EventGeo, EventLeads, EventPrecision, EventTier, Place}
import com.histmersion.api.library.{DiscoverResult, Label, Source}
import com.histmersion.api.media.MediaThumb
import com.histmersion.api.research.Article
import com.histmersion.api.views.{EventDetail, PlaceDetail}

/** THE link preview. One delegated hover handler on the document answers for every element that carries `data-ck` (a
  * source) or `data-slug` (an article) — server-rendered anchors in a window or on a standalone page, and the web UI's
  * own [[SourceLink]]s alike — so nothing has to be wired per link, and a link added later previews too.
  *
  * Data comes TYPED through [[ApiClient]] (no DOM scraping, no `js.Dynamic`), once per id: the cache holds the
  * `Future`, so ten hovers over the same citation cost one request.
  */
object Preview:
  type CacheKey = CacheKey.T
  object CacheKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DistanceKm = DistanceKm.T
  object DistanceKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ElementSize = ElementSize.T
  object ElementSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImagePixels = ImagePixels.T
  object ImagePixels extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeyboardDriven = KeyboardDriven.T
  object KeyboardDriven extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestoreFocus = RestoreFocus.T
  object RestoreFocus extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ScreenPixels = ScreenPixels.T
  object ScreenPixels extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ViewportHeight = ViewportHeight.T
  object ViewportHeight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ViewportWidth = ViewportWidth.T
  object ViewportWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** What an element asks to preview. */

  type PinUnderKeyboard = _root_.com.histmersion.web.Preview.KeyboardDriven
  val PinUnderKeyboard: _root_.com.histmersion.web.Preview.KeyboardDriven.type = _root_.com.histmersion.web.Preview.KeyboardDriven
  type ShowAtX = _root_.com.histmersion.web.Preview.ScreenPixels
  val ShowAtX: _root_.com.histmersion.web.Preview.ScreenPixels.type = _root_.com.histmersion.web.Preview.ScreenPixels
  type ShowAtY = _root_.com.histmersion.web.Preview.ScreenPixels
  val ShowAtY: _root_.com.histmersion.web.Preview.ScreenPixels.type = _root_.com.histmersion.web.Preview.ScreenPixels
  type MoveToX = _root_.com.histmersion.web.Preview.ScreenPixels
  val MoveToX: _root_.com.histmersion.web.Preview.ScreenPixels.type = _root_.com.histmersion.web.Preview.ScreenPixels
  type MoveToY = _root_.com.histmersion.web.Preview.ScreenPixels
  val MoveToY: _root_.com.histmersion.web.Preview.ScreenPixels.type = _root_.com.histmersion.web.Preview.ScreenPixels
  type PointFitH = _root_.com.histmersion.web.Preview.ElementSize
  val PointFitH: _root_.com.histmersion.web.Preview.ElementSize.type = _root_.com.histmersion.web.Preview.ElementSize
  type PointFitVh = _root_.com.histmersion.web.Preview.ViewportHeight
  val PointFitVh: _root_.com.histmersion.web.Preview.ViewportHeight.type = _root_.com.histmersion.web.Preview.ViewportHeight
  type UnderFitTop = _root_.com.histmersion.web.Preview.ScreenPixels
  val UnderFitTop: _root_.com.histmersion.web.Preview.ScreenPixels.type = _root_.com.histmersion.web.Preview.ScreenPixels
  type UnderFitBottom = _root_.com.histmersion.web.Preview.ScreenPixels
  val UnderFitBottom: _root_.com.histmersion.web.Preview.ScreenPixels.type = _root_.com.histmersion.web.Preview.ScreenPixels
  type UnderFitH = _root_.com.histmersion.web.Preview.ElementSize
  val UnderFitH: _root_.com.histmersion.web.Preview.ElementSize.type = _root_.com.histmersion.web.Preview.ElementSize
  type UnderFitVh = _root_.com.histmersion.web.Preview.ViewportHeight
  val UnderFitVh: _root_.com.histmersion.web.Preview.ViewportHeight.type = _root_.com.histmersion.web.Preview.ViewportHeight
  type PointPlacementX = _root_.com.histmersion.web.Preview.ScreenPixels
  val PointPlacementX: _root_.com.histmersion.web.Preview.ScreenPixels.type = _root_.com.histmersion.web.Preview.ScreenPixels
  type PointPlacementY = _root_.com.histmersion.web.Preview.ScreenPixels
  val PointPlacementY: _root_.com.histmersion.web.Preview.ScreenPixels.type = _root_.com.histmersion.web.Preview.ScreenPixels
  type PointPlacementW = _root_.com.histmersion.web.Preview.ElementSize
  val PointPlacementW: _root_.com.histmersion.web.Preview.ElementSize.type = _root_.com.histmersion.web.Preview.ElementSize
  type PointPlacementH = _root_.com.histmersion.web.Preview.ElementSize
  val PointPlacementH: _root_.com.histmersion.web.Preview.ElementSize.type = _root_.com.histmersion.web.Preview.ElementSize
  type PointPlacementVw = _root_.com.histmersion.web.Preview.ViewportWidth
  val PointPlacementVw: _root_.com.histmersion.web.Preview.ViewportWidth.type = _root_.com.histmersion.web.Preview.ViewportWidth
  type PointPlacementVh = _root_.com.histmersion.web.Preview.ViewportHeight
  val PointPlacementVh: _root_.com.histmersion.web.Preview.ViewportHeight.type = _root_.com.histmersion.web.Preview.ViewportHeight
  type UnderPlacementLeft = _root_.com.histmersion.web.Preview.ScreenPixels
  val UnderPlacementLeft: _root_.com.histmersion.web.Preview.ScreenPixels.type = _root_.com.histmersion.web.Preview.ScreenPixels
  type UnderPlacementTop = _root_.com.histmersion.web.Preview.ScreenPixels
  val UnderPlacementTop: _root_.com.histmersion.web.Preview.ScreenPixels.type = _root_.com.histmersion.web.Preview.ScreenPixels
  type UnderPlacementBottom = _root_.com.histmersion.web.Preview.ScreenPixels
  val UnderPlacementBottom: _root_.com.histmersion.web.Preview.ScreenPixels.type = _root_.com.histmersion.web.Preview.ScreenPixels
  type UnderPlacementWidth = _root_.com.histmersion.web.Preview.ElementSize
  val UnderPlacementWidth: _root_.com.histmersion.web.Preview.ElementSize.type = _root_.com.histmersion.web.Preview.ElementSize
  type UnderPlacementW = _root_.com.histmersion.web.Preview.ElementSize
  val UnderPlacementW: _root_.com.histmersion.web.Preview.ElementSize.type = _root_.com.histmersion.web.Preview.ElementSize
  type UnderPlacementH = _root_.com.histmersion.web.Preview.ElementSize
  val UnderPlacementH: _root_.com.histmersion.web.Preview.ElementSize.type = _root_.com.histmersion.web.Preview.ElementSize
  type UnderPlacementVw = _root_.com.histmersion.web.Preview.ViewportWidth
  val UnderPlacementVw: _root_.com.histmersion.web.Preview.ViewportWidth.type = _root_.com.histmersion.web.Preview.ViewportWidth
  type UnderPlacementVh = _root_.com.histmersion.web.Preview.ViewportHeight
  val UnderPlacementVh: _root_.com.histmersion.web.Preview.ViewportHeight.type = _root_.com.histmersion.web.Preview.ViewportHeight

  enum Target:
    case Source(citekey: Target.Source.Citekey)
    case Article(slug: Target.Article.Slug)

    /** A gazetteer place on the map, with its hierarchy read as of `asOf` (the map's year). */
    case Place(id: Target.Place.Id, asOf: Option[Target.Place.AsOf])

    /** A historical event (its row in the events list). */
    case Event(id: Target.Event.Id)

  object Target:
    object Source:
      type Citekey = Citekey.T
      object Citekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Article:
      type Slug = com.histmersion.web.Preview.Item.Slug
      val Slug: _root_.com.histmersion.web.Preview.Item.Slug.type = _root_.com.histmersion.web.Preview.Item.Slug
    object Place:
      type Id = com.histmersion.web.Pages.PlaceId
      val Id: _root_.com.histmersion.web.Pages.PlaceId.type = _root_.com.histmersion.web.Pages.PlaceId
      type AsOf = com.histmersion.api.Years.Astronomical
      val AsOf: _root_.com.histmersion.api.Years.Astronomical.type = _root_.com.histmersion.api.Years.Astronomical
    object Event:
      type Id = com.histmersion.web.Pages.EventId
      val Id: _root_.com.histmersion.web.Pages.EventId.type = _root_.com.histmersion.web.Pages.EventId

  /** Pure: which preview the attributes ask for. A source wins when both are present. */
  def targetOf(ck: Option[com.histmersion.web.Preview.Target.Source.Citekey], slug: Option[com.histmersion.web.Preview.Item.Slug]): Option[Target] =
    ck.map(_.trim)
      .filter(_.nonEmpty)
      .map(Target.Source(_))
      .orElse(slug.map(_.trim).filter(_.nonEmpty).map(Target.Article(_)))

  /** The preview card of a source, as data — pure, so what it shows is a unit test. */
  final case class Card(
      title: Card.Title,
      byline: Card.Byline,
      identifier: Card.Identifier,
      status: Option[com.histmersion.api.library.Source.Status],
      chips: List[(Card.ChipsPart1, Card.ChipsPart2)],
      where: Card.Where,
      summary: Option[Card.Summary],
      /** Short facts, one per line (a place's parents and its counts). */
      facts: List[Card.Facts] = Nil,
      /** Pages elsewhere, as (text, href): a place's Wikidata, GeoNames or Pleiades page. */
      links: List[(Card.LinksPart1, Card.LinksPart2)] = Nil,
      /** The picture a place or event card shows, with its credit and licence (#1446). */
      media: Option[MediaThumb] = None,
      /** What the card is about before anything is read — an icon of [[Glyphs]] chosen by [[CardIcons]] (an event's
        * kind, a site's type, a region, a source's type…) — and an event's TIER (`attested` | `catalogue`, #1449).
        */
      icon: Option[Card.Icon] = None,
      tier: Option[Card.Tier] = None,
      /** An event's sections (#1449): the sources attesting it, the articles citing them, the findings near it, the
        * sources that may discuss it (unconfirmed), its catalogues, its leads.
        */
      sections: List[Preview.Section] = Nil
  )

  object Card:
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Byline = Byline.T
    object Byline extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Identifier = Identifier.T
    object Identifier extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ChipsPart1 = ChipsPart1.T
    object ChipsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ChipsPart2 = ChipsPart2.T
    object ChipsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Where = Where.T
    object Where extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Summary = Summary.T
    object Summary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Facts = Facts.T
    object Facts extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinksPart1 = LinksPart1.T
    object LinksPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinksPart2 = LinksPart2.T
    object LinksPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Icon = Icon.T
    object Icon extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tier = Tier.T
    object Tier extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** One titled list on a card. */
  final case class Section(title: Section.Title, items: List[Item], note: Option[Section.Note] = None, kind: Section.Kind = com.histmersion.web.Preview.Section.Kind(""))

  object Section:
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Note = Note.T
    object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** One line: text, optionally a source (`citekey` → a source link with its own card), an article (`slug`), a page
    * elsewhere (`href`), a command to copy, a literature query to run.
    */
  final case class Item(
      text: Item.Text,
      citekey: Option[Item.Citekey] = None,
      slug: Option[Item.Slug] = None,
      href: Option[Item.Href] = None,
      command: Option[Item.Command] = None,
      query: Option[Item.Query] = None
  )

  object Item:
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Citekey = Citekey.T
    object Citekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Href = Href.T
    object Href extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Command = Command.T
    object Command extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Query = Query.T
    object Query extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val SummaryChars = 300

  def card(s: com.histmersion.api.library.Source.Detail): Card =
    val ref = com.histmersion.api.library.Source.Ref(s.citekey, s.doi, s.authors, s.year, s.title, s.status)
    Card(
      title = s.title.value,
      byline = List(s.authors.map(_.value).mkString(", "), s.year.map(_.value.toInt).fold("")(_.toString), s.venue.map(_.value).getOrElse(""))
        .filter(_.trim.nonEmpty)
        .mkString(" · "),
      identifier = ref.idLabel,
      status = Some(ref.status),
      chips = s.topics.map("topic" -> _) ++ s.tags.map("tag" -> _),
      where = List(s.regions.map(Label.display).mkString(", "), YearFmt.period(s.periodStart, s.periodEnd))
        .filter(_.nonEmpty)
        .mkString(" · "),
      summary = s.summary
        .map(_.trim)
        .filter(_.nonEmpty)
        .map(t => if t.length > SummaryChars then t.take(SummaryChars - 1) + "…" else t),
      icon = Some(CardIcons.source(s))
    )

  /** A source's card with its picture — a book's cover, else a paper's illustration (#1446). */
  def card(sw: com.histmersion.api.library.Source.WithFindings): Card =
    card(sw.source).modify(
      _.media).setTo(sw.illustration).modify(_.icon).setTo(Some(CardIcons.source(sw.source, hasCover = sw.illustration.exists(_.role == MediaRole.Cover)))
    )

  def card(a: com.histmersion.api.research.Article.View): Card =
    Card(
      a.article.title,
      Label.display(a.article.kind),
      a.article.slug,
      None,
      Nil,
      YearFmt.period(a.article.periodStart, a.article.periodEnd),
      a.article.summary.map(_.trim).filter(_.nonEmpty),
      icon = Some(CardIcons.Article)
    )

  private def plural(n: com.histmersion.api.RowCount, one: com.histmersion.api.Plural.Noun.Singular): com.histmersion.api.Plural.Phrase = s"$n $one${if n == 1 then "" else "s"}"

  /** Where a place comes from, in words: the external source that brought it in, else the seed's own source line
    * ("Pleiades (pleiades.stoa.org), CC-BY").
    */
  private[web] def datasetOf(p: com.histmersion.api.catalogue.Place.Summary): Option[com.histmersion.api.LpfDataset.Title] =
    p.importSource
      .map { k =>
        if k.startsWith("lpf:") then s"LPF dataset ${k.drop(4)}"
        else com.histmersion.api.catalogue.Place.Link.schemeTitle(k)
      }
      .orElse(p.source)
      .map(_.trim)
      .filter(_.nonEmpty)

  /** A place's card: what it is (name, kind, its period as the source states it — "end 1769 CE, start unknown" — its
    * description), whether a region has a boundary yet, where it comes from with a link to that page, the places it is
    * part of, and what the corpus holds for it (the counts are what clicking it selects).
    */
  def card(d: PlaceDetail): Card =
    val p = d.place
    val contested = Option.when(d.contested && !p.label.toLowerCase.contains("contested"))("contested extent")
    val description = d.descriptions match
      case Nil           => None
      case first :: rest =>
        val other =
          rest.find(_.lang != first.lang).map(o => s" · ${o.text}" + (if o.lang.nonEmpty then s" (${o.lang})" else ""))
        Some(first.text + other.getOrElse(""))
    val links = d.links
      .flatMap(l =>
        com.histmersion.api.catalogue.Place.Link
          .url(l.scheme, l.externalId)
          .map(u => s"${com.histmersion.api.catalogue.Place.Link.schemeTitle(l.scheme)} ${l.externalId}" -> u)
      )
      .distinct
      .take(4)
    Card(
      title = p.label,
      byline = (List(Label.display(p.kind).capitalize, p.when) ++ contested)
        .filter(_.nonEmpty)
        .mkString(" · "),
      identifier = p.id.value,
      status = None,
      chips = Nil,
      where = if d.parents.isEmpty then com.histmersion.web.Preview.Card.Where("") else "part of " + d.parents.map(_.label).mkString(", "),
      summary = description,
      facts = Option.when(d.datedByFindings)(com.histmersion.api.catalogue.Place.TimeBasis.FindingsNote).toList ++
        d.shapeNotes ++
        datasetOf(p).map(s => s"source: $s").toList ++
        d.counts.toList.map(c =>
          List(
            plural(c.sources, com.histmersion.api.Plural.Noun.Singular("source")),
            plural(c.findings, com.histmersion.api.Plural.Noun.Singular("finding")),
            plural(c.articles, com.histmersion.api.Plural.Noun.Singular("article")),
            plural(c.events, com.histmersion.api.Plural.Noun.Singular("event"))
          ).mkString(" · ")
        ),
      links = links,
      media = d.media.headOption,
      icon = Some(CardIcons.place(p.kind, d.siteType))
    )

  /** An event's card: what it is, when, how sure, its description, and its picture (#1446). */
  def card(d: EventDetail): Card =
    val e = d.event
    val years = YearFmt.span(e.timeSpan) + EventGeo.missingEnd(e.timeSpan).fold("")(m => s" ($m)")
    Card(
      title = e.label,
      byline = List(Label.display(e.kind).capitalize, e.datingNote.getOrElse(years), Label.display(e.historicity))
        .filter(_.nonEmpty)
        .mkString(" · "),
      identifier = e.id,
      status = None,
      chips = Nil,
      where = d.parent.fold("")(p => s"part of ${p.label}"),
      summary = e.description
        .map(_.trim)
        .filter(_.nonEmpty)
        .map(t => if t.length > SummaryChars then t.take(SummaryChars - 1) + "…" else t),
      links = d.wikidata
        .flatMap(q => com.histmersion.api.catalogue.Place.Link.url(com.histmersion.api.catalogue.Place.Link.UrlScheme("wikidata"), q).map(u => s"Wikidata $q" -> u))
        .toList,
      facts = eventFacts(d, years),
      media = d.media.headOption,
      icon = Some(CardIcons.event(e.kind)),
      tier = Some(d.tier),
      sections = eventSections(d)
    )

  /** Pure: an event card's facts — the span a stated dating covers, how precisely it is dated ([[EventPrecision]]: an
    * imprecise one is not on the map at a year), and the fields corrected by hand (kept over the catalogue's on a
    * re-import).
    */
  def eventFacts(d: EventDetail, years: com.histmersion.api.Years.Text): List[com.histmersion.api.UiLabels.Text] =
    val e = d.event
    Option.when(e.datingNote.isDefined)(s"window: $years").toList ++
      EventPrecision
        .label(e.precision)
        .map(l => if e.precision == EventPrecision.Imprecise then s"$l — listed, not drawn on the map at a year" else l)
        .toList ++
      d.corrections.map(c => s"corrected by hand — ${Corrections.describe(c)}")

  /** Pure: an event card's sections (#1449), in the order a reader asks: who attests it, where it is written about,
    * what the corpus holds around it, what may discuss it, where the catalogue record is from, and what to do next.
    * Empty sections are left out.
    */
  def eventSections(d: EventDetail): List[Section] =
    def km(x: com.histmersion.web.Preview.DistanceKm) = s"${math.round(x)} km"
    List(
      Section(
        com.histmersion.web.Preview.Section.Title("Attested by"),
        d.sources.map(s =>
          Item(s.title.getOrElse(s.citekey) + s.quote.fold("")(q => s" — “$q”"), citekey = Some(s.citekey))
        ),
        kind = com.histmersion.web.Preview.Section.Kind("sources")
      ),
      Section(
        com.histmersion.web.Preview.Section.Title("Articles citing those sources"),
        d.articles.map(a => Item(a.title, slug = Some(a.slug))),
        kind = com.histmersion.web.Preview.Section.Kind("articles")
      ),
      Section(
        com.histmersion.web.Preview.Section.Title("Findings near it"),
        d.findings.map(f =>
          Item(
            s"${km(f.km)}, ${if f.yearGap == 0 then "same time" else s"${f.yearGap} yrs apart"}: ${Fmt.clip(f.claim, com.histmersion.api.Markdown.CharLimit(90))}",
            citekey = Some(f.citekey)
          )
        ),
        kind = com.histmersion.web.Preview.Section.Kind("findings")
      ),
      Section(
        com.histmersion.web.Preview.Section.Title("Possibly discussed in"),
        d.candidates.map(c =>
          Item(
            c.title.getOrElse(c.citekey.value.toString) + (if c.reasons.isEmpty then "" else s" — ${c.reasons.mkString("; ")}"),
            citekey = Some(c.citekey.value.toString)
          )
        ),
        note = Some(com.histmersion.web.Preview.Section.Note("unconfirmed — proposed from findings near it and passages naming it")),
        kind = com.histmersion.web.Preview.Section.Kind("candidates")
      ),
      Section(
        com.histmersion.web.Preview.Section.Title("Catalogue"),
        d.catalogue.map(c =>
          Item(
            s"${c.title} ${c.providerId}" + EventLeads.size(c.vei, c.magnitude).fold("")(s => s" ($s)") +
              s" · ${c.licence} · cite: ${c.citation}",
            href = Some(c.url).filter(_.nonEmpty)
          )
        ),
        note = Option.when(d.tier == EventTier.Catalogue)(com.histmersion.web.Preview.Section.Note("known only from a catalogue: a research lead, not evidence")),
        kind = com.histmersion.web.Preview.Section.Kind("catalogue")
      ),
      Section(
        com.histmersion.web.Preview.Section.Title("Leads"),
        d.leads.flatMap(l =>
          Item(l.text) :: l.actions.map(a =>
            Item(a.label, command = Some(a.command), query = a.query.filter(_ => a.kind == "discover"))
          )
        ),
        kind = com.histmersion.web.Preview.Section.Kind("leads")
      )
    ).filter(_.items.nonEmpty)

  /** Pure: the line under a card's picture — its credit and licence (the licence a link when it has a page), as the
    * licence asks for it to be shown.
    */
  def creditLine(t: MediaThumb): (com.histmersion.api.UiLabels.Text, Option[(com.histmersion.web.Preview.Item.Href, com.histmersion.api.UiLabels.Text)]) =
    val lic = t.license.map(l => l -> t.licenseUrl.orElse(LicenseName.url(l)).orElse(t.sourceUrl).getOrElse(""))
    (t.credit, lic.map((l, u) => (l, u)))

  // ── typed, cached fetches ────────────────────────────────────────────────────────────────────

  // one per kind, each bounded (#1949): a long session hovers thousands of links
  private val sourceCache = BoundedCache[String, Future[Card]](Layout.cardCacheSize)
  private val articleCache = BoundedCache[String, Future[Card]](Layout.cardCacheSize)
  private val placeCache = BoundedCache[String, Future[Card]](Layout.cardCacheSize)
  private val eventCache = BoundedCache[String, Future[Card]](Layout.cardCacheSize)

  /** The card caches' figures, for [[Diagnostics]]. */
  def cacheStats: List[(com.histmersion.api.UiLabels.Text, BoundedCache.Stats)] =
    List(
      "cards.source" -> sourceCache,
      "cards.article" -> articleCache,
      "cards.place" -> placeCache,
      "cards.event" -> eventCache
    ).map((n, c) => n -> BoundedCache.stats(c))

  /** A failed fetch is forgotten, so the next hover tries again. */
  private def cached(cache: BoundedCache[com.histmersion.web.Preview.CacheKey, Future[Card]], key: com.histmersion.web.Preview.CacheKey)(fetch: => Future[Card]): Future[Card] =
    cache.getOrElseUpdate(
      key, {
        val f = fetch
        f.onComplete {
          case Failure(_) => if cache.peek(key).contains(f) then { val _ = cache.remove(key) }; case _ => ()
        }
        f
      }
    )

  /** Drop a source's cached card — it was just edited, and the next hover must show the edit. */
  def forget(ck: com.histmersion.web.Preview.Target.Source.Citekey): Unit = { val _ = sourceCache.remove(ck) }

  def fetch(t: Target): Future[Card] = t match
    case Target.Source(ck)      => cached(sourceCache, ck)(ApiClient.getSource(ck, body = com.histmersion.web.ApiClient.IncludeBody(false)).map(card))
    case Target.Article(slug)   => cached(articleCache, slug)(ApiClient.getArticle(slug, brief = com.histmersion.web.ApiClient.Brief(true)).map(card))
    case Target.Place(id, asOf) =>
      cached(placeCache, s"$id@${asOf.getOrElse("")}")(ApiClient.place(id, asOf, counts = com.histmersion.web.ApiClient.Counts(true)).map(card))
    case Target.Event(id) => cached(eventCache, id)(ApiClient.getEvent(id).map(card))

  // ── the one handler ──────────────────────────────────────────────────────────────────────────

  private val Selector = "[data-ck],[data-slug],[data-place],[data-event]"

  /** The year a place chip's card reads the gazetteer at — the map's; set by whoever owns the map. */
  // `var`: the app's map sets it once it exists; a page without a map reads places as of now
  var placeAsOf: () => Option[com.histmersion.api.Years.Astronomical] = () => None

  /** Pure: what an element asks to preview, a place chip's id included (a source or an article first). */
  def targetOf(ck: Option[com.histmersion.web.Preview.Target.Source.Citekey], slug: Option[com.histmersion.web.Preview.Item.Slug], place: Option[com.histmersion.web.Pages.PlaceId], asOf: => Option[Long]): Option[Target] =
    targetOf(ck, slug).orElse(place.map(_.trim).filter(_.nonEmpty).map(Target.Place(_, asOf)))

  /** Pure: the same, an event's id last. */
  def targetOf(
      ck: Option[com.histmersion.web.Preview.Target.Source.Citekey],
      slug: Option[com.histmersion.web.Preview.Item.Slug],
      place: Option[com.histmersion.web.Pages.PlaceId],
      event: Option[com.histmersion.web.Pages.EventId],
      asOf: => Option[Long]
  ): Option[Target] =
    targetOf(ck, slug, place, asOf).orElse(event.map(_.trim).filter(_.nonEmpty).map(Target.Event(_)))

  private def targetOfElement(el: dom.Element): Option[Target] =
    targetOf(
      Option(el.getAttribute("data-ck")),
      Option(el.getAttribute("data-slug")),
      Option(el.getAttribute("data-place")),
      Option(el.getAttribute("data-event")),
      placeAsOf()
    )

  private def placeAnchorOf(t: dom.EventTarget): Option[dom.Element] = t match
    case el: dom.Element => Option(el.closest("[data-place]"))
    case _               => None

  /** The place chip a key or focus event is FOR — the chip itself, not its ✕ inside it. */
  private def chipItself(t: dom.EventTarget): Option[dom.Element] =
    placeAnchorOf(t).filter(el => t match { case n: dom.Element => n == el; case _ => false })

  /** A click on a place chip pins its card under it (its ✕ stops the click: removing is not reading). */
  private def onDocClick(e: dom.MouseEvent): Unit =
    placeAnchorOf(e.target) match
      case Some(el) if !e.defaultPrevented => pinUnder(el, keyboard = com.histmersion.web.Preview.PinUnderKeyboard(false))
      case _                               =>
        // a click anywhere else in the page, away from the pinned card and what pinned it, lets it go
        // (a click on the map is the map's to judge: it may land on another place, [[PlaceHover]])
        val inside = e.target match
          case n: dom.Node =>
            pinned.exists(p => p.box.contains(n) || p.owner.exists(_.contains(n))) ||
            n.isInstanceOf[dom.Element] && n.asInstanceOf[dom.Element].closest(".map-container") != null
          case _ => false
        if !inside then unpin(restoreFocus = com.histmersion.web.Preview.RestoreFocus(false))

  private def pinUnder(el: dom.Element, keyboard: com.histmersion.web.Preview.PinUnderKeyboard): Unit =
    targetOfElement(el).foreach { t =>
      if isPinned(t) && !keyboard then unpin(restoreFocus = com.histmersion.web.Preview.RestoreFocus(false))
      else
        pin(
          t,
          () =>
            Option.when(el.isConnected) {
              val r = el.getBoundingClientRect()
              Anchor.Under(r.left, r.top, r.bottom, r.width)
            },
          owner = Some(el),
          keyboard = keyboard
        )
    }

  /** The element the tooltip is currently for, and the tooltip itself. A single floating element, re-rendered in place
    * — only one preview is ever visible.
    */
  private var current: Option[dom.Element] = None
  private var tip: Option[(dom.HTMLElement, RootNode)] = None
  // `var`: installing twice (the app and a page entry point both call it) must not double-bind
  private var installed = false

  def install(): Unit =
    if !installed then
      installed = true
      // pointer events, so a TOUCH can be told apart: a tap follows the link, it does not float a card
      document.addEventListener("pointerover", (e: dom.PointerEvent) => if e.pointerType != "touch" then onOver(e))
      document.addEventListener("pointerout", (e: dom.PointerEvent) => onOut(e))
      // a scroll moves a pinned card with its anchor in the next frame, not inside the scroll event: a
      // position written there is a scroll-linked effect (Firefox warns, and it lags behind async scrolling)
      document.addEventListener("scroll", (_: dom.Event) => { hide(); repositionSoon() }, useCapture = true)
      window.addEventListener("resize", (_: dom.Event) => reposition())
      // the pinned card takes Escape first (the window manager's Escape runs after, on `window`)
      document.addEventListener(
        "keydown",
        (e: dom.KeyboardEvent) =>
          if PinRules.dismisses(e.key) && !e.defaultPrevented && pinned.isDefined then
            e.preventDefault()
            unpin()
      )
      // a place chip ([data-place]) answers like a place on the map: a click or Enter pins its card
      document.addEventListener("click", (e: dom.MouseEvent) => onDocClick(e))
      document.addEventListener(
        "keydown",
        (e: dom.KeyboardEvent) =>
          if PinRules.activates(e.key) then
            chipItself(e.target).foreach { el =>
              e.preventDefault()
              pinUnder(el, keyboard = com.histmersion.web.Preview.PinUnderKeyboard(true))
            }
      )
      document.addEventListener(
        "focusin",
        (e: dom.FocusEvent) =>
          chipItself(e.target).filterNot(current.contains).foreach(el => targetOfElement(el).foreach(show(el, _)))
      )
      document.addEventListener(
        "focusout",
        (e: dom.FocusEvent) => chipItself(e.target).filter(current.contains).foreach(_ => hide())
      )

  private def anchorOf(t: dom.EventTarget): Option[dom.Element] = t match
    case el: dom.Element => Option(el.closest(Selector))
    case _               => None

  private def onOver(e: dom.MouseEvent): Unit =
    anchorOf(e.target).filterNot(current.contains).foreach(el => targetOfElement(el).foreach(show(el, _)))

  private def onOut(e: dom.MouseEvent): Unit =
    current.foreach { el =>
      val to = Option(e.relatedTarget).collect { case n: dom.Node => n }
      if !to.exists(n => el.contains(n)) then hide()
    }

  private def show(el: dom.Element, t: Target): Unit =
    hide()
    if !isPinned(t) then showUnder(el, t)

  private def showUnder(el: dom.Element, t: Target): Unit =
    current = Some(el)
    val box = document.createElement("div").asInstanceOf[dom.HTMLElement]
    box.className = t match
      case _: Target.Place | _: Target.Event => "cite-tooltip place-tip"
      case _                                 => "cite-tooltip"
    document.body.appendChild(box)
    val content = Var[Option[Card]](None)
    val root = render(box, div(child <-- content.signal.map(_.fold(div(cls := "cite-tip-meta", "loading…"))(cardView))))
    tip = Some(box -> root)
    place(el, box)
    watch(box)(() => if current.contains(el) then place(el, box))
    fetch(t).onComplete {
      case Success(c) if current.contains(el) =>
        content.set(Some(c))
        place(el, box)
      case Failure(_) if current.contains(el) => hide()
      case _                                  => ()
    }

  def hide(): Unit =
    tipObserver.foreach { o =>
      val _ = o.disconnect()
    }
    tipObserver = None
    tip.foreach { (box, root) => root.unmount(); box.remove() }
    tip = None
    current = None
    atPoint = None

  // ── a card at a point: the map's places, which are shapes rather than links ─────────────────────

  /** The target of the card floating at the pointer, if that is the card showing. */
  private var atPoint: Option[Target] = None

  /** Is the card at the pointer showing `t`? */
  def showingAt(t: Target): Boolean = atPoint.contains(t) && tip.isDefined

  /** Float `t`'s card beside the viewport point (`x`, `y`) — the same card, the same cache. A second call for the
    * target already showing only moves it.
    */
  def showAt(t: Target, x: com.histmersion.web.Preview.ShowAtX, y: com.histmersion.web.Preview.ShowAtY): Unit =
    if showingAt(t) then moveTo(x, y)
    else if isPinned(t) then hide() // its card is already up, pinned: no second, moving copy
    else
      hide()
      atPoint = Some(t)
      val box = document.createElement("div").asInstanceOf[dom.HTMLElement]
      box.className = "cite-tooltip place-tip"
      box.setAttribute("role", "tooltip")
      document.body.appendChild(box)
      val content = Var[Option[Card]](None)
      val root =
        render(box, div(child <-- content.signal.map(_.fold(div(cls := "cite-tip-meta", "loading…"))(cardView))))
      tip = Some(box -> root)
      moveTo(x, y)
      watch(box)(() => if atPoint.contains(t) then lastPoint.foreach((px, py) => moveTo(px, py)))
      fetch(t).onComplete {
        case Success(c) if atPoint.contains(t) =>
          content.set(Some(c))
          moveTo(x, y)
        case Failure(_) if atPoint.contains(t) => hide()
        case _                                 => ()
      }

  /** Hide the card at the pointer if it is `t`'s — a card for another target stays. */
  def hideAt(t: Target): Unit = if atPoint.contains(t) then hide()

  /** Beside the pointer, below and to the right; flipped to the other side when it would leave the viewport. The
    * position is data for `.cite-tooltip`.
    */
  def moveTo(x: com.histmersion.web.Preview.MoveToX, y: com.histmersion.web.Preview.MoveToY): Unit =
    lastPoint = Some((x, y))
    tip.filter(_ => atPoint.isDefined).foreach { (box, _) =>
      val maxW = math.min(360.0, window.innerWidth - 20)
      box.style.setProperty("--tip-maxw", s"${maxW}px")
      val (w, h) = naturalSize(box)
      apply(box, pointFit(x, y, w, h, window.innerWidth, window.innerHeight), h)
    }

  // `var`s: the pointer the floating card was last placed beside (a size change re-places it there),
  // and the observer that re-places the hover card when its content grows (a picture, a late section)
  private var lastPoint: Option[(com.histmersion.web.Preview.ScreenPixels, com.histmersion.web.Preview.ScreenPixels)] = None
  private var tipObserver: Option[js.Dynamic] = None

  /** Re-run `f` whenever the card's content changes size — a picture arriving, a section filling in after the card was
    * placed — so it never grows past an edge from where it was put.
    */
  private def watch(box: dom.HTMLElement)(f: () => Unit): Unit =
    tipObserver.foreach { o =>
      val _ = o.disconnect()
    }
    tipObserver = observe(box, f)

  private def observe(box: dom.HTMLElement, f: () => Unit): Option[js.Dynamic] =
    if js.typeOf(js.Dynamic.global.ResizeObserver) == "undefined" then None
    else
      val cb: js.Function1[js.Any, Unit] = _ => f()
      val o = js.Dynamic.newInstance(js.Dynamic.global.ResizeObserver)(cb)
      Option(box.firstElementChild).foreach { c =>
        val _ = o.observe(c)
      }
      Some(o)

  /** The card's width, and the height its content wants — not the height a cap gave it last time. */
  private def naturalSize(box: dom.HTMLElement): (com.histmersion.web.Preview.ElementSize, com.histmersion.web.Preview.ElementSize) =
    val b = box.getBoundingClientRect()
    (b.width, box.scrollHeight.toDouble + (box.offsetHeight - box.clientHeight))

  /** Put the card where `fit` says; a card taller than its room is capped there and scrolls (once pinned), and a capped
    * hover card fades out at the bottom to say there is more.
    */
  private def apply(box: dom.HTMLElement, fit: Fit, natural: com.histmersion.web.Preview.ElementSize): Unit =
    box.style.setProperty("--tip-x", s"${fit.x}px")
    box.style.setProperty("--tip-y", s"${fit.y}px")
    box.style.setProperty("--tip-maxh", s"${fit.maxH}px")
    if natural > fit.maxH + 1 then box.classList.add("is-clipped") else box.classList.remove("is-clipped")

  /** Where a card goes: its top-left corner, and the most height it may take there. */
  final case class Fit(x: Fit.X, y: Fit.Y, maxH: Fit.MaxH)

  object Fit:
    type X = X.T
    object X extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Y = Y.T
    object Y extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxH = MaxH.T
    object MaxH extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The margin a card keeps from every viewport edge, and the least room worth placing it in beside its anchor; with
    * less on both sides it uses the whole height and covers the anchor instead.
    */
  private val Margin = 10.0
  private val MinRoom = 160.0

  /** Pure: the vertical half of a placement — below the anchor (`top`…`bottom`) when the card fits there, else above it
    * when it fits there, else on the roomier side, capped to that room so the content scrolls; with less than `MinRoom`
    * either side, the full viewport height.
    */
  def verticalFit(top: com.histmersion.web.Preview.ScreenPixels, bottom: com.histmersion.web.Preview.ScreenPixels, gap: com.histmersion.web.Preview.ElementSize, h: com.histmersion.web.Preview.ElementSize, vh: com.histmersion.web.Preview.ViewportHeight): (com.histmersion.web.Preview.ScreenPixels, com.histmersion.web.Preview.ScreenPixels) =
    val below = vh - Margin - (bottom + gap)
    val above = top - gap - Margin
    if h <= below then (bottom + gap, below)
    else if h <= above then (top - gap - h, above)
    else if math.max(below, above) < MinRoom then (Margin, vh - 2 * Margin)
    else if below >= above then (bottom + gap, below)
    else (Margin, above)

  /** Pure: beside a pointer at (`x`, `y`) — 14 px below-right of it, on the other side of the pointer when an edge
    * would be crossed, shifted to stay ≥ 10 px inside the viewport, and capped in height when neither side has room for
    * all of it.
    */
  def pointFit(x: com.histmersion.web.Preview.ScreenPixels, y: com.histmersion.web.Preview.ScreenPixels, w: com.histmersion.web.Preview.ElementSize, h: com.histmersion.web.Preview.PointFitH, vw: com.histmersion.web.Preview.ViewportWidth, vh: com.histmersion.web.Preview.PointFitVh): Fit =
    val gap = 14.0
    val left0 = if x + gap + w > vw - Margin then x - gap - w else x + gap
    val left = math.max(Margin, math.min(left0, vw - w - Margin))
    val (top, maxH) = verticalFit(y, y, gap, h, vh)
    Fit(left, top, maxH)

  /** Pure: centred 6 px under an element spanning (`left`, `top`)–(`left + width`, `bottom`), above it when there is no
    * room below, capped in height when neither side has room.
    */
  def underFit(
      left: com.histmersion.web.Preview.ScreenPixels,
      top: com.histmersion.web.Preview.UnderFitTop,
      bottom: com.histmersion.web.Preview.UnderFitBottom,
      width: com.histmersion.web.Preview.ElementSize,
      w: com.histmersion.web.Preview.ElementSize,
      h: com.histmersion.web.Preview.UnderFitH,
      vw: com.histmersion.web.Preview.ViewportWidth,
      vh: com.histmersion.web.Preview.UnderFitVh
  ): Fit =
    val x = math.max(Margin, math.min(left + width / 2 - w / 2, vw - w - Margin))
    val (y, maxH) = verticalFit(top, bottom, 6.0, h, vh)
    Fit(x, y, maxH)

  /** Pure: the corner [[pointFit]] picks (kept for callers that only need the position). */
  def pointPlacement(x: com.histmersion.web.Preview.PointPlacementX, y: com.histmersion.web.Preview.PointPlacementY, w: com.histmersion.web.Preview.PointPlacementW, h: com.histmersion.web.Preview.PointPlacementH, vw: com.histmersion.web.Preview.PointPlacementVw, vh: com.histmersion.web.Preview.PointPlacementVh): (com.histmersion.web.Preview.ScreenPixels, com.histmersion.web.Preview.ScreenPixels) =
    val f = pointFit(x, y, w, h, vw, vh)
    (f.x, f.y)

  /** Pure: the corner [[underFit]] picks. */
  def underPlacement(
      left: com.histmersion.web.Preview.UnderPlacementLeft,
      top: com.histmersion.web.Preview.UnderPlacementTop,
      bottom: com.histmersion.web.Preview.UnderPlacementBottom,
      width: com.histmersion.web.Preview.UnderPlacementWidth,
      w: com.histmersion.web.Preview.UnderPlacementW,
      h: com.histmersion.web.Preview.UnderPlacementH,
      vw: com.histmersion.web.Preview.UnderPlacementVw,
      vh: com.histmersion.web.Preview.UnderPlacementVh
  ): (com.histmersion.web.Preview.ScreenPixels, com.histmersion.web.Preview.ScreenPixels) =
    val f = underFit(left, top, bottom, width, w, h, vw, vh)
    (f.x, f.y)

  // ── a PINNED card: a click (or Enter) keeps it still, so its links can be followed ──────────────

  /** Where a pinned card is anchored, as the viewport sees it now. */
  enum Anchor:
    /** Beside a point — a place on the map, where its hover card was. */
    case At(x: Anchor.At.X, y: Anchor.At.Y)

    /** Under an element — a place chip — flipped above it when there is no room. */
    case Under(left: Anchor.Under.Left, top: Anchor.Under.Top, bottom: Anchor.Under.Bottom, width: Anchor.Under.Width)

  object Anchor:
    object At:
      type X = com.histmersion.web.Preview.ScreenPixels
      val X: _root_.com.histmersion.web.Preview.ScreenPixels.type = _root_.com.histmersion.web.Preview.ScreenPixels
      type Y = com.histmersion.web.Preview.ScreenPixels
      val Y: _root_.com.histmersion.web.Preview.ScreenPixels.type = _root_.com.histmersion.web.Preview.ScreenPixels
    object Under:
      type Left = com.histmersion.web.Preview.ScreenPixels
      val Left: _root_.com.histmersion.web.Preview.ScreenPixels.type = _root_.com.histmersion.web.Preview.ScreenPixels
      type Top = com.histmersion.web.Preview.ScreenPixels
      val Top: _root_.com.histmersion.web.Preview.ScreenPixels.type = _root_.com.histmersion.web.Preview.ScreenPixels
      type Bottom = com.histmersion.web.Preview.ScreenPixels
      val Bottom: _root_.com.histmersion.web.Preview.ScreenPixels.type = _root_.com.histmersion.web.Preview.ScreenPixels
      type Width = com.histmersion.web.Preview.ElementSize
      val Width: _root_.com.histmersion.web.Preview.ElementSize.type = _root_.com.histmersion.web.Preview.ElementSize

  /** The pinned card: what it shows, its element, where it is anchored (`None` while the anchor is out of view: the
    * card waits, hidden), what pinned it (a click there is not "outside"), and where the focus goes back to when it
    * closes.
    */
  final private class Pinned(
      val target: Target,
      val box: dom.HTMLElement,
      val root: RootNode,
      val anchor: () => Option[Anchor],
      val owner: Option[dom.Element],
      val returnFocus: Option[dom.HTMLElement]
  ):
    // `var`: the card's size, measured when its content arrives — a pan re-places it every frame
    // and must not force a layout each time
    var size: (com.histmersion.web.Preview.ElementSize, com.histmersion.web.Preview.ElementSize) = (com.histmersion.web.Preview.ElementSize(0), com.histmersion.web.Preview.ElementSize(0))
    // `var`: re-measures the card when its content changes size (see `observe`)
    var observer: Option[js.Dynamic] = None


  // `var`: at most one card is pinned
  private var pinned: Option[Pinned] = None

  def isPinned(t: Target): Boolean = pinned.exists(p => sameCard(p.target, t))

  /** Pure: do two targets show the same card? A place is the same place whichever layer read it, at whichever year (the
    * map's span and the filter round its middle differently).
    */
  def sameCard(a: Target, b: Target): Boolean = (a, b) match
    case (Target.Place(x, _), Target.Place(y, _)) => x == y
    case _                                        => a == b
  def pinnedTarget: Option[Target] = pinned.map(_.target)

  /** Pin `t`'s card at `anchor` — replacing any pinned card and the hover card. From the keyboard the focus moves into
    * the card (its ✕ first, then its links), and back to `owner` when it closes.
    */
  def pin(t: Target, anchor: () => Option[Anchor], owner: Option[dom.Element] = None, keyboard: com.histmersion.web.Preview.KeyboardDriven = com.histmersion.web.Preview.KeyboardDriven(false)): Unit =
    unpin(restoreFocus = com.histmersion.web.Preview.RestoreFocus(false))
    hide()
    val box = document.createElement("div").asInstanceOf[dom.HTMLElement]
    box.className = "cite-tooltip place-tip is-pinned"
    box.setAttribute("role", "dialog")
    box.setAttribute("aria-label", "place")
    document.body.appendChild(box)
    val content = Var[Option[Card]](None)
    val close = button(
      typ := "button",
      cls := "btn btn--bare btn--icon btn--sm cite-tip-close",
      aria.label := "close the card",
      title := "Close (Escape)",
      "✕",
      onClick --> (_ => unpin())
    )
    val root =
      render(box, div(close, child <-- content.signal.map(_.fold(div(cls := "cite-tip-meta", "loading…"))(cardView))))
    val focusBack = if keyboard then owner.collect { case h: dom.HTMLElement => h } else None
    val p = Pinned(t, box, root, anchor, owner, focusBack)
    pinned = Some(p)
    measure(p)
    reposition()
    p.observer = observe(box, () => if pinned.contains(p) then { measure(p); reposition() })
    if keyboard then close.ref.focus()
    fetch(t).onComplete {
      case Success(c) if pinned.contains(p) =>
        content.set(Some(c))
        box.setAttribute("aria-label", c.title)
        measure(p)
        reposition()
      case Failure(_) if pinned.contains(p) => unpin(restoreFocus = com.histmersion.web.Preview.RestoreFocus(false))
      case _                                => ()
    }

  /** Close the pinned card; from the keyboard, the focus returns to what pinned it. */
  def unpin(restoreFocus: com.histmersion.web.Preview.RestoreFocus = com.histmersion.web.Preview.RestoreFocus(true)): Unit =
    pinned.foreach { p =>
      pinned = None
      p.observer.foreach { o =>
        val _ = o.disconnect()
      }
      val hadFocus = Option(document.activeElement).exists(a => p.box.contains(a))
      p.root.unmount()
      p.box.remove()
      if restoreFocus || hadFocus then p.returnFocus.filter(_.isConnected).foreach(_.focus())
    }

  private def measure(p: Pinned): Unit =
    val maxW = math.min(360.0, window.innerWidth - 20)
    p.box.style.setProperty("--tip-maxw", s"${maxW}px")
    p.size = naturalSize(p.box)

  /** Keep the pinned card at its anchor — after a pan or zoom of the map, a scroll, a resize. */
  // `var`: a reposition is asked for the next frame (one per frame however many scroll events)
  private var repositionAsked = false
  private def repositionSoon(): Unit =
    if pinned.isDefined && !repositionAsked then
      repositionAsked = true
      val _ = window.requestAnimationFrame { _ => repositionAsked = false; reposition() }

  def reposition(): Unit =
    pinned.foreach { p =>
      p.anchor() match
        case None    => p.box.classList.add("is-offscreen")
        case Some(a) =>
          p.box.classList.remove("is-offscreen")
          val (w, h) = p.size
          val fit = a match
            case Anchor.At(ax, ay)         => pointFit(ax, ay, w, h, window.innerWidth, window.innerHeight)
            case Anchor.Under(l, t, b, wd) => underFit(l, t, b, wd, w, h, window.innerWidth, window.innerHeight)
          apply(p.box, fit, h)
    }

  /** Under the link, flipped above it when it would leave the viewport, capped when it fits neither. */
  private def place(anchor: dom.Element, box: dom.HTMLElement): Unit =
    val r = anchor.getBoundingClientRect()
    val maxW = math.min(440.0, window.innerWidth - 20)
    // the position is data for `.cite-tooltip`, not a style of its own
    box.style.setProperty("--tip-maxw", s"${maxW}px")
    val (w, h) = naturalSize(box)
    apply(box, underFit(r.left, r.top, r.bottom, r.width, w, h, window.innerWidth, window.innerHeight), h)

  private[web] def cardView(c: Card): HtmlElement =
    div(
      div(
        cls := "cite-tip-title",
        c.icon.map(i =>
          span(cls := "cite-tip-icon", onMountCallback(ctx => ctx.thisNode.ref.innerHTML = Glyphs.svg(i)))
        ),
        c.title,
        c.tier.map(t =>
          span(cls := s"cite-tip-tier tier-$t", if t == EventTier.Catalogue then "catalogue only" else "attested")
        )
      ),
      Option.when(c.byline.nonEmpty)(div(cls := "cite-tip-meta", c.byline)),
      c.media.map(mediaView),
      div(cls := "cite-tip-ident", c.identifier),
      Option.when(c.chips.nonEmpty)(div(cls := "cite-tip-chips", c.chips.map((_, v) => Chip.value(v)))),
      Option.when(c.where.nonEmpty)(div(cls := "cite-tip-geo", c.where)),
      c.status.map(s => div(cls := "cite-tip-status", StatusBadge(s))),
      c.facts.map(f => div(cls := "cite-tip-fact", f)),
      Option.when(c.links.nonEmpty)(
        div(
          cls := "cite-tip-links",
          c.links.map((t, href) =>
            a(
              cls := "cite-tip-link",
              com.raquo.laminar.api.L.href := href,
              target := "_blank",
              rel := "noopener",
              t + " ↗"
            )
          )
        )
      ),
      c.summary.map(s => div(cls := "cite-tip-summary", s)),
      c.sections.map(sectionView)
    )

  /** One section of an event card: a source line is a source link (its own card on hover, its window on click); a
    * lead's discover action asks the existing literature search and lists what it found; a command can be copied.
    */
  private def sectionView(sec: Section): HtmlElement =
    div(
      cls := s"cite-tip-section section-${sec.kind}",
      div(cls := "cite-tip-section-title", sec.title),
      sec.note.map(n => div(cls := "cite-tip-section-note", n)),
      ul(cls := "cite-tip-items", sec.items.map(itemView))
    )

  private def itemView(it: Item): HtmlElement =
    li(
      cls := "cite-tip-item",
      it.citekey.map(ck => SourceLink(ck)),
      // a discover action is its button alone; anything else says what it is
      Option.when(it.query.isEmpty)(
        it.slug
          .map(slug => com.histmersion.web.ArticleLink(slug, it.text): HtmlElement)
          .getOrElse(span(if it.citekey.isDefined then s" ${it.text}" else it.text))
      ),
      it.href.map(u => a(cls := "cite-tip-link", href := u, target := "_blank", rel := "noopener", " ↗")),
      it.query.map(q => discoverButton(q)),
      it.command.filter(_ => it.query.isEmpty).map(cmd => commandView(cmd))
    )

  private def commandView(cmd: com.histmersion.web.Preview.Item.Command): HtmlElement =
    span(
      cls := "cite-tip-command",
      code(cmd),
      button(
        typ := "button",
        cls := "btn btn--bare btn--sm",
        title := "copy the command",
        "copy",
        onClick --> { _ => scala.util.Try(dom.window.navigator.clipboard.writeText(cmd)) }
      )
    )

  /** Ask the literature search (`/api/discover`, OpenAlex) for `q`, works not in the corpus yet, and list the first few
    * under the button.
    */
  private def discoverButton(q: com.histmersion.api.Api.Search.Text): HtmlElement =
    val found = Var[Option[Either[String, DiscoverResult]]](None)
    span(
      cls := "cite-tip-discover",
      button(
        typ := "button",
        cls := "btn btn--sm",
        "search the literature",
        onClick --> { _ =>
          found.set(Some(Left("searching…")))
          ApiClient.discover(q, com.histmersion.api.Paging.Limit(5)).onComplete {
            case Success(r) => found.set(Some(Right(r)))
            case Failure(e) => found.set(Some(Left(s"no answer: ${e.getMessage}")))
          }
        }
      ),
      child <-- found.signal.map {
        case None           => emptyNode
        case Some(Left(m))  => div(cls := "cite-tip-meta", m)
        case Some(Right(r)) =>
          if r.works.isEmpty then div(cls := "cite-tip-meta", s"nothing found for “$q”")
          else
            ul(
              cls := "cite-tip-items",
              r.works.map(w =>
                li(
                  cls := "cite-tip-item",
                  w.title.map(_.value).getOrElse(w.doi.map(_.value).getOrElse("untitled")) + w.year.fold("")(y => s" ($y)"),
                  w.doi.map(d =>
                    a(
                      cls := "cite-tip-link",
                      href := s"https://doi.org/$d",
                      target := "_blank",
                      rel := "noopener",
                      " ↗"
                    )
                  )
                )
              )
            )
      }
    )

  /** A card's picture: the local thumbnail (never the provider's URL — a hover stays on this server), and under it the
    * credit line and the licence, linked to the licence's page. Its size is the stored thumbnail's, as `--w`/`--h`, so
    * the card does not jump when the image arrives.
    */
  private def mediaView(t: MediaThumb): HtmlElement =
    val (credit, lic) = creditLine(t)
    figure(
      cls := "cite-tip-media",
      // a book's cover is shown whole (a portrait), a photograph of a place fills the card's width
      cls("is-cover") := t.role == MediaRole.Cover,
      onMountCallback(ctx =>
        sizeOf(t).foreach { (w, h) =>
          ctx.thisNode.ref.style.setProperty("--w", w.toString)
          ctx.thisNode.ref.style.setProperty("--h", h.toString)
        }
      ),
      img(
        cls := "cite-tip-thumb is-zoomable",
        src := t.url,
        alt := credit,
        loadingAttr := "lazy",
        title := "open full size",
        // a click opens the stored picture full size, with its credit, licence and provider's page (Lightbox)
        onMountCallback(ctx => fullAttrs(t).foreach((k, v) => ctx.thisNode.ref.setAttribute(k, v)))
      ),
      figCaption(
        cls := "cite-tip-credit",
        span(credit),
        lic.map((l, u) =>
          val name = LicenseName.label(l)
          if u.isEmpty then span(" · ", name)
          else span(" · ", a(cls := "cite-tip-link", href := u, target := "_blank", rel := "noopener license", name))
        )
      )
    )

  /** Pure: the attributes that open a card's picture full size ([[Lightbox]]): the stored file, its credit, licence and
    * the provider's page.
    */
  def fullAttrs(t: MediaThumb): List[(com.histmersion.api.UiLabels.Text, com.histmersion.api.UiLabels.Text)] =
    val item = Lightbox.Item(
      MediaThumb.filePath(t.uid),
      t.credit,
      None,
      Some(t.credit).filter(_.nonEmpty),
      None,
      t.license,
      t.licenseUrl,
      t.sourceUrl,
      None
    )
    val vals = List(
      "data-full" -> Some(item.src),
      "data-lb-credit" -> item.credit,
      "data-lb-license" -> item.license,
      "data-lb-license-url" -> item.licenseUrl,
      "data-lb-source-url" -> item.sourceUrl
    )
    vals.collect { case (k, Some(v)) if v.nonEmpty => k -> v }

  /** Pure: the thumbnail's stored size (`--w`/`--h` for `.cite-tip-media`), when it is known. */
  def sizeOf(t: MediaThumb): Option[(com.histmersion.web.Preview.ImagePixels, com.histmersion.web.Preview.ImagePixels)] = (t.width, t.height) match
    case (Some(w), Some(h)) if w > 0 && h > 0 => Some((w, h))
    case _                                    => None

  private val loadingAttr = htmlAttr("loading", com.raquo.laminar.codecs.StringAsIsCodec)
