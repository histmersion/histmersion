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
  type SourceKindResult = SourceKindResult.T
  object SourceKindResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceKindKind = SourceKindKind.T
  object SourceKindKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceKindMarker = SourceKindMarker.T
  object SourceKindMarker extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KindOfResult = KindOfResult.T
  object KindOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KindOfHasCover = KindOfHasCover.T
  object KindOfHasCover extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceResult = SourceResult.T
  object SourceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceHasCover = SourceHasCover.T
  object SourceHasCover extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceResult = PlaceResult.T
  object PlaceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceKind = PlaceKind.T
  object PlaceKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceSiteType = PlaceSiteType.T
  object PlaceSiteType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SiteMarkerResult = SiteMarkerResult.T
  object SiteMarkerResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SiteMarkerSiteType = SiteMarkerSiteType.T
  object SiteMarkerSiteType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventResult = EventResult.T
  object EventResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventKind = EventKind.T
  object EventKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HtmlResult = HtmlResult.T
  object HtmlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HtmlIcon = HtmlIcon.T
  object HtmlIcon extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
  def sourceKind(kind: com.histmersion.web.CardIcons.SourceKindKind, marker: com.histmersion.web.CardIcons.SourceKindMarker = false): com.histmersion.web.CardIcons.SourceKindResult = kind match
    case Source.Kinds.Book    => "book"
    case Source.Kinds.Chapter => "book-open"
    case Source.Kinds.Paper   => "file-text"
    case Source.Kinds.Thesis  => "graduation-cap"
    case Source.Kinds.Dataset => "database"
    case _                    => if marker then Unknown else GenericSource

  /** Pure: what a source is, from its record ([[SourceKinds.of]]). */
  def kindOf(s: Source.Detail, hasCover: com.histmersion.web.CardIcons.KindOfHasCover = false): com.histmersion.web.CardIcons.KindOfResult =
    Source.Kinds.of(s.doi.map(_.value), s.venue.map(_.value), s.provenance, s.tags, s.topics, hasCover)

  def source(s: Source.Detail, hasCover: com.histmersion.web.CardIcons.SourceHasCover = false): com.histmersion.web.CardIcons.SourceResult = sourceKind(kindOf(s, hasCover))

  /** Pure: a place's icon — a site its type's (the one its marker draws, else a pin), a natural feature its type's when
    * it has one (a cave) else a mountain, a region or polity a map, a people a group.
    */
  def place(kind: com.histmersion.web.CardIcons.PlaceKind, siteType: Option[com.histmersion.web.CardIcons.PlaceSiteType]): com.histmersion.web.CardIcons.PlaceResult =
    val typed = siteType.flatMap(SiteTypes.icon)
    kind match
      case Place.Kind.Site                       => typed.getOrElse(GenericPlace)
      case Place.Kind.Feature                    => typed.getOrElse(Feature)
      case Place.Kind.Region | Place.Kind.Polity => Area
      case Place.Kind.People                     => People
      case _                                     => GenericPlace

  /** Pure: a site's MARKER icon — its type's, the exclamation mark when the type is unknown. */
  def siteMarker(siteType: Option[com.histmersion.web.CardIcons.SiteMarkerSiteType]): com.histmersion.web.CardIcons.SiteMarkerResult = siteType.flatMap(SiteTypes.icon).getOrElse(Unknown)

  def event(kind: com.histmersion.web.CardIcons.EventKind): com.histmersion.web.CardIcons.EventResult = EventTaxonomy.icon(kind)

  /** Pure: a card icon's markup, for the cards built as HTML (a popup): the same element, class and size as
    * [[Preview.cardView]]'s.
    */
  def html(icon: com.histmersion.web.CardIcons.HtmlIcon): com.histmersion.web.CardIcons.HtmlResult = s"""<span class="cite-tip-icon">${Glyphs.svg(icon)}</span>"""
