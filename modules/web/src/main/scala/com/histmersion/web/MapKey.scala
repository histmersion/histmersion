package com.histmersion
package web

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.catalogue.{EventTaxonomy, SiteTypes}
import com.histmersion.api.library.Source
import io.github.iltotore.iron.constraint.any.Pure

/** The map's KEY to its shapes and icons (#1449): what each shape is, the findings' source kinds, and — while their
  * layers are on — the event kinds (natural, human) and site types, each icon with its name. Pure markup.
  */
object MapKey:
  type Events = Events.T
  object Events extends Newtype[Boolean, Pure]
  type EventsHtml = EventsHtml.T
  object EventsHtml extends Newtype[String, Pure]
  type Html = Html.T
  object Html extends Newtype[String, Pure]
  type ShapesHtml = ShapesHtml.T
  object ShapesHtml extends Newtype[String, Pure]
  type Sites = Sites.T
  object Sites extends Newtype[Boolean, Pure]
  type SitesHtml = SitesHtml.T
  object SitesHtml extends Newtype[String, Pure]
  type Undated = Undated.T
  object Undated extends Newtype[Boolean, Pure]

  type UndatedHtml = UndatedHtml.T
  object UndatedHtml extends Newtype[String, Pure]

  def eventsHtml: EventsHtml =
    EventsHtml(
      EventTaxonomy.groups.map { g =>
        val items = EventTaxonomy.kinds
          .filter(_.group == g)
          .map(k =>
            s"""<li><span class="hd-badge hd-badge--square hd-key-badge">${Glyphs.badge(Glyphs.Name(k.icon.value))}</span><span>${Fmt.esc(
                k.label.value
              )}</span></li>"""
          )
          .mkString
        s"""<div class="hd-key-group"><div class="hd-key-title">$g events</div><ul class="hd-key-list">$items</ul></div>"""
      }.mkString +
        """<div class="hd-key-group"><ul class="hd-key-list hd-key-tiers">""" +
        """<li><span class="hd-badge hd-ev hd-ev--attested hd-key-swatch"></span><span>attested by a source</span></li>""" +
        """<li><span class="hd-badge hd-ev hd-ev--catalogue hd-key-swatch"></span><span>catalogue only (a lead)</span></li></ul></div>"""
    )

  def sitesHtml: SitesHtml =
    val items = (SiteTypes.all.map(t => t.icon.value -> t.label.value) :+ (CardIcons.Unknown -> "site of unknown type"))
      .map((icon, label) =>
        s"""<li><span class="hd-badge hd-badge--circle hd-key-badge">${Glyphs.badge(Glyphs.Name(icon))}</span><span>${Fmt.esc(
            label
          )}</span></li>"""
      )
      .mkString
    SitesHtml(
      s"""<div class="hd-key-group"><div class="hd-key-title">sites</div><ul class="hd-key-list">$items</ul></div>"""
    )

  /** The shape grammar (docs/visualization-guide.md §2): the SHAPE says what kind of thing a marker is, the icon its
    * type, the fill its date, the outline its tier — and the findings' icons, by what their source is. Shown whatever
    * layers are on: the findings always are.
    */
  def shapesHtml: ShapesHtml =
    def badge(shape: String, inner: String) = s"""<span class="hd-badge hd-badge--$shape hd-key-badge">$inner</span>"""
    // the same icons as the toolbar's layer switches
    val shapes = PointKinds.all.map(k => Icon.html(k.icon).value -> k.meaning.value) ++ List(
      Icon.html(Icon.Region).value -> "a region or polity (its boundary)",
      DotCluster.bubbleHtml(DotCluster.Count(5), DotCluster.Size(20), (DotCluster.StartLabel("var(--accent)"), DotCluster.EndLabel("var(--accent)")), DotCluster.Id("hdb-key"), DotCluster.ExtraClass("hd-key-cluster"), None).value ->
        "several nearby (click to list)",
      // the clusters of events keep the event's square (#1647)
      DotCluster.bubbleHtml(
        DotCluster.Count(5),
        DotCluster.Size(20),
        (DotCluster.StartLabel("var(--accent)"), DotCluster.EndLabel("var(--accent)")),
        DotCluster.Id("hdb-key-ev"),
        DotCluster.ExtraClass("hd-key-cluster"),
        None,
        BubbleForm.Events
      ).value ->
        "several events nearby",
      DotCluster.bubbleHtml(
        DotCluster.Count(5),
        DotCluster.Size(20),
        (DotCluster.StartLabel("var(--accent)"), DotCluster.EndLabel("var(--accent)")),
        DotCluster.Id("hdb-key-mix"),
        DotCluster.ExtraClass("hd-key-cluster"),
        None,
        BubbleForm.WithEvents
      ).value ->
        "several nearby, events among them"
    )
    val kinds = Source.Kinds.all.map(k =>
      badge("diamond", Glyphs.badge(CardIcons.sourceKind(k, marker = true)).value) ->
        (if k.value == Source.Kinds.Unknown then "source of unknown kind" else k.value)
    )
    def list(items: List[(String, String)]) =
      items.map((m, label) => s"""<li>$m<span>${Fmt.esc(label)}</span></li>""").mkString
    ShapesHtml(
      s"""<div class="hd-key-group"><div class="hd-key-title">shapes</div><ul class="hd-key-list">${list(
          shapes
        )}</ul></div>""" +
        s"""<div class="hd-key-group"><div class="hd-key-title">findings, by source</div><ul class="hd-key-list">${list(
            kinds
          )}</ul></div>"""
    )

  /** The key's markup: the shapes, then the icons of the layers on. */
  def html(events: Events, sites: Sites, undated: Undated = Undated(false)): Html =
    val body = shapesHtml.value + (if events.value then eventsHtml.value else "") + (if sites.value then sitesHtml.value else "")
    Html(
      (if undated.value then undatedHtml.value else "") + s"""<details class="hd-key"><summary>key</summary>$body</details>"""
    )

  /** The legend's entry while the sites or events of no known date are drawn: their dashed outline. */
  val undatedHtml: UndatedHtml =
    UndatedHtml(
      """<div class="hd-legend-undated"><span class="hd-badge hd-badge--circle hd-key-badge hd-undated-swatch" aria-hidden="true"></span>""" +
        """<span>date unknown (dashed outline)</span></div>"""
    )
