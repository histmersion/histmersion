package com.histmersion.web

import com.histmersion.api.catalogue.{EventTaxonomy, SiteTypes}
import com.histmersion.api.research.Article
import com.histmersion.api.catalogue.Place
import com.histmersion.api.library.Source

/** The icon at the head of every card ([[Preview.cardView]]) and of every row a map cluster lists — one per KIND of
  * thing, from the one registry ([[Glyphs]]), so a reader sees what a card is about before reading it, whichever layer
  * it came from: an event its kind's icon ([[EventTaxonomy]]), a site the icon its marker has ([[SiteTypes]]), a region
  * or polity a map, a source what it is (a book, a chapter, a paper, a thesis, a dataset), an article a notebook, a
  * picture an image. Pure.
  */
object CardIcons:
  /** A source of no recognisable type (a blank page: a paper is the page with lines), and a place of no recognisable
    * kind.
    */
  val GenericSource = "file"
  val GenericPlace = "map-pin"

  /** A region's or a polity's card: the shapes the map draws. */
  val Area = "map"
  val People = "users"
  val Feature = "mountain-snow"

  /** A compiled wiki article. */
  val Article = "notebook-text"

  /** A picture on the media layer. */
  val Media = "image"

  /** A thing on the map whose type is not known: its kind's shape with this mark, never a bare dot. */
  val Unknown = "alert-mark"

  /** Pure: a source kind's icon ([[SourceKinds]]); `unknown` the generic document on a card, the exclamation mark on
    * the map (`marker = true`).
    */
  def sourceKind(kind: Source.Kinds.Wire, marker: Boolean = false): Glyphs.Name = Glyphs.Name(kind.value match
    case Source.Kinds.Book    => "book"
    case Source.Kinds.Chapter => "book-open"
    case Source.Kinds.Paper   => "file-text"
    case Source.Kinds.Thesis  => "graduation-cap"
    case Source.Kinds.Dataset => "database"
    case _                    => if marker then Unknown else GenericSource)

  /** Pure: what a source is, from its record ([[SourceKinds.of]]). */
  def kindOf(s: Source.Detail, hasCover: Boolean = false): Source.Kinds.Wire =
    Source.Kinds.of(s.doi, s.venue, s.provenance, s.tags, s.topics, hasCover)

  def source(s: Source.Detail, hasCover: Boolean = false): Glyphs.Name = sourceKind(kindOf(s, hasCover))

  /** Pure: a place's icon — a site its type's (the one its marker draws, else a pin), a natural feature its type's when
    * it has one (a cave) else a mountain, a region or polity a map, a people a group.
    */
  def place(kind: com.histmersion.api.catalogue.Place.Kind.Wire, siteType: Option[com.histmersion.api.catalogue.Place.Entry.SiteType]): Glyphs.Name =
    val typed = siteType.flatMap(t => SiteTypes.icon(SiteTypes.IconId(t.value))).map(_.value)
    Glyphs.Name(kind.value match
      case Place.Kind.Site                       => typed.getOrElse(GenericPlace)
      case Place.Kind.Feature                    => typed.getOrElse(Feature)
      case Place.Kind.Region | Place.Kind.Polity => Area
      case Place.Kind.People                     => People
      case _                                     => GenericPlace)

  /** Pure: a site's MARKER icon — its type's, the exclamation mark when the type is unknown. */
  def siteMarker(siteType: Option[com.histmersion.api.catalogue.Place.Entry.SiteType]): Glyphs.Name = Glyphs.Name(siteType.flatMap(t => SiteTypes.icon(SiteTypes.IconId(t.value))).map(_.value).getOrElse(Unknown))

  def event(kind: com.histmersion.api.catalogue.EventSummary.Kind): com.histmersion.api.catalogue.EventTaxonomy.Kind.Icon = EventTaxonomy.icon(kind)

  /** Pure: a card icon's markup, for the cards built as HTML (a popup): the same element, class and size as
    * [[Preview.cardView]]'s.
    */
  def html(icon: Glyphs.Name): String = s"""<span class="cite-tip-icon">${Glyphs.svg(icon)}</span>"""
