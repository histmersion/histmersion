package com.histmersion.web

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.catalogue.{EventTaxonomy, SiteTypes}
import com.histmersion.api.library.SourceKinds

/** The map's KEY to its shapes and icons (#1449): what each shape is, the findings' source kinds, and — while their
  * layers are on — the event kinds (natural, human) and site types, each icon with its name. Pure markup.
  */
object MapKey:
  def eventsHtml: String =
    EventTaxonomy.groups.map { g =>
      val items = EventTaxonomy.kinds
        .filter(_.group == g)
        .map(k =>
          s"""<li><span class="hd-badge hd-badge--square hd-key-badge">${Glyphs.badge(k.icon)}</span><span>${Fmt.esc(
              k.label
            )}</span></li>"""
        )
        .mkString
      s"""<div class="hd-key-group"><div class="hd-key-title">$g events</div><ul class="hd-key-list">$items</ul></div>"""
    }.mkString +
      """<div class="hd-key-group"><ul class="hd-key-list hd-key-tiers">""" +
      """<li><span class="hd-badge hd-ev hd-ev--attested hd-key-swatch"></span><span>attested by a source</span></li>""" +
      """<li><span class="hd-badge hd-ev hd-ev--catalogue hd-key-swatch"></span><span>catalogue only (a lead)</span></li></ul></div>"""

  def sitesHtml: String =
    val items = (SiteTypes.all.map(t => t.icon -> t.label) :+ (CardIcons.Unknown -> "site of unknown type"))
      .map((icon, label) =>
        s"""<li><span class="hd-badge hd-badge--circle hd-key-badge">${Glyphs.badge(icon)}</span><span>${Fmt.esc(
            label
          )}</span></li>"""
      )
      .mkString
    s"""<div class="hd-key-group"><div class="hd-key-title">sites</div><ul class="hd-key-list">$items</ul></div>"""

  /** The shape grammar (docs/visualization-guide.md §2): the SHAPE says what kind of thing a marker is, the icon its
    * type, the fill its date, the outline its tier — and the findings' icons, by what their source is. Shown whatever
    * layers are on: the findings always are.
    */
  def shapesHtml: String =
    def badge(shape: String, inner: String) = s"""<span class="hd-badge hd-badge--$shape hd-key-badge">$inner</span>"""
    // the same icons as the toolbar's layer switches
    val shapes = PointKinds.all.map(k => Icon.html(k.icon) -> k.meaning) ++ List(
      Icon.html(Icon.Region) -> "a region or polity (its boundary)",
      DotCluster.bubbleHtml(5, 20, ("var(--accent)", "var(--accent)"), "hdb-key", "hd-key-cluster", None) ->
        "several nearby (click to list)",
      // the clusters of events keep the event's square (#1647)
      DotCluster.bubbleHtml(
        5,
        20,
        ("var(--accent)", "var(--accent)"),
        "hdb-key-ev",
        "hd-key-cluster",
        None,
        BubbleForm.Events
      ) ->
        "several events nearby",
      DotCluster.bubbleHtml(
        5,
        20,
        ("var(--accent)", "var(--accent)"),
        "hdb-key-mix",
        "hd-key-cluster",
        None,
        BubbleForm.WithEvents
      ) ->
        "several nearby, events among them"
    )
    val kinds = SourceKinds.all.map(k =>
      badge("diamond", Glyphs.badge(CardIcons.sourceKind(k, marker = true))) ->
        (if k == SourceKinds.Unknown then "source of unknown kind" else k)
    )
    def list(items: List[(String, String)]) =
      items.map((m, label) => s"""<li>$m<span>${Fmt.esc(label)}</span></li>""").mkString
    s"""<div class="hd-key-group"><div class="hd-key-title">shapes</div><ul class="hd-key-list">${list(
        shapes
      )}</ul></div>""" +
      s"""<div class="hd-key-group"><div class="hd-key-title">findings, by source</div><ul class="hd-key-list">${list(
          kinds
        )}</ul></div>"""

  /** The key's markup: the shapes, then the icons of the layers on. */
  def html(events: Boolean, sites: Boolean, undated: Boolean = false): String =
    val body = shapesHtml + (if events then eventsHtml else "") + (if sites then sitesHtml else "")
    (if undated then undatedHtml else "") + s"""<details class="hd-key"><summary>key</summary>$body</details>"""

  /** The legend's entry while the sites or events of no known date are drawn: their dashed outline. */
  val undatedHtml: String =
    """<div class="hd-legend-undated"><span class="hd-badge hd-badge--circle hd-key-badge hd-undated-swatch" aria-hidden="true"></span>""" +
      """<span>date unknown (dashed outline)</span></div>"""
