package com.histmersion.web

import com.histmersion.api.*

/** The icon at the head of every card ([[Preview.cardView]]) and of every row a map cluster lists — one
  * per KIND of thing, from the one registry ([[Glyphs]]), so a reader sees what a card is about before
  * reading it, whichever layer it came from: an event its kind's icon ([[EventTaxonomy]]), a site the
  * icon its marker has ([[SiteTypes]]), a region or polity a map, a source what it is (a book, a chapter,
  * a paper, a thesis, a dataset), an article a notebook, a picture an image. Pure. */
object CardIcons:

  /** A source of no recognisable type (a blank page: a paper is the page with lines), and a place of no
    * recognisable kind. */
  val GenericSource = "file"
  val GenericPlace  = "map-pin"
  /** A region's or a polity's card: the shapes the map draws. */
  val Area     = "map"
  val People   = "users"
  val Feature  = "mountain-snow"
  /** A compiled wiki article. */
  val Article  = "notebook-text"
  /** A picture on the media layer. */
  val Media    = "image"

  /** A thing on the map whose type is not known: its kind's shape with this mark, never a bare dot. */
  val Unknown  = "alert-mark"

  /** Pure: a source kind's icon ([[SourceKinds]]); `unknown` the generic document on a card, the
    * exclamation mark on the map (`marker = true`). */
  def sourceKind(kind: String, marker: Boolean = false): String = kind match
    case SourceKinds.Book    => "book"
    case SourceKinds.Chapter => "book-open"
    case SourceKinds.Paper   => "file-text"
    case SourceKinds.Thesis  => "graduation-cap"
    case SourceKinds.Dataset => "database"
    case _                   => if marker then Unknown else GenericSource

  /** Pure: what a source is, from its record ([[SourceKinds.of]]). */
  def kindOf(s: SourceDetail, hasCover: Boolean = false): String =
    SourceKinds.of(s.doi, s.venue, s.provenance, s.tags, s.topics, hasCover)

  def source(s: SourceDetail, hasCover: Boolean = false): String = sourceKind(kindOf(s, hasCover))

  /** Pure: a place's icon — a site its type's (the one its marker draws, else a pin), a natural feature
    * its type's when it has one (a cave) else a mountain, a region or polity a map, a people a group. */
  def place(kind: String, siteType: Option[String]): String =
    val typed = siteType.flatMap(SiteTypes.icon)
    kind match
      case PlaceKind.Site                     => typed.getOrElse(GenericPlace)
      case PlaceKind.Feature                  => typed.getOrElse(Feature)
      case PlaceKind.Region | PlaceKind.Polity => Area
      case PlaceKind.People                   => People
      case _                                  => GenericPlace

  /** Pure: a site's MARKER icon — its type's, the exclamation mark when the type is unknown. */
  def siteMarker(siteType: Option[String]): String = siteType.flatMap(SiteTypes.icon).getOrElse(Unknown)

  def event(kind: String): String = EventTaxonomy.icon(kind)

  /** Pure: a card icon's markup, for the cards built as HTML (a popup): the same element, class and size
    * as [[Preview.cardView]]'s. */
  def html(icon: String): String = s"""<span class="cite-tip-icon">${Glyphs.svg(icon)}</span>"""
