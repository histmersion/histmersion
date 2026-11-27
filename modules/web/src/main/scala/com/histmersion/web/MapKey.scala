package com.histmersion
package web

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.catalogue.{EventTaxonomy, SiteTypes}
import com.histmersion.api.library.Source
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Html
import com.histmersion.api.Html.html as markup
import com.histmersion.lint.{tolerated, LintRule}

/** The map's KEY to its shapes and icons (#1449): what each shape is, the findings' source kinds, and — while their
  * layers are on — the event kinds (natural, human) and site types, each icon with its name. Pure markup.
  */
object MapKey:
  enum Events derives CanEqual:
    case Shown, Hidden
  object Events:
    extension (events: Events) def shown: Boolean = events == Shown
  enum Sites derives CanEqual:
    case Shown, Hidden
  object Sites:
    extension (sites: Sites) def shown: Boolean = sites == Shown
  enum Undated derives CanEqual:
    case Shown, Hidden
  object Undated:
    extension (undated: Undated) def shown: Boolean = undated == Shown

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def eventsHtml: Html =
    Html.concat(EventTaxonomy.groups.map { g =>
      val items = Html.concat(
        EventTaxonomy.kinds
          .filter(_.group == g)
          .map(k =>
            markup"""<li><span class="hd-badge hd-badge--square hd-key-badge">${Glyphs.badge(
                Glyphs.Name(k.icon.value)
              )}</span><span>${k.label}</span></li>"""
          )
      )
      markup"""<div class="hd-key-group"><div class="hd-key-title">$g events</div><ul class="hd-key-list">$items</ul></div>"""
    }) ++
      markup"""<div class="hd-key-group"><ul class="hd-key-list hd-key-tiers">""" ++
      markup"""<li><span class="hd-badge hd-ev hd-ev--attested hd-key-swatch"></span><span>attested by a source</span></li>""" ++
      markup"""<li><span class="hd-badge hd-ev hd-ev--catalogue hd-key-swatch"></span><span>catalogue only (a lead)</span></li></ul></div>"""

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def sitesHtml: Html =
    val items = Html.concat(
      (SiteTypes.all.map(t => t.icon.value -> t.label.value) :+ (CardIcons.Unknown -> "site of unknown type"))
        .map((icon, label) =>
          markup"""<li><span class="hd-badge hd-badge--circle hd-key-badge">${Glyphs.badge(
              Glyphs.Name(icon)
            )}</span><span>$label</span></li>"""
        )
    )
    markup"""<div class="hd-key-group"><div class="hd-key-title">sites</div><ul class="hd-key-list">$items</ul></div>"""

  /** The shape grammar (docs/visualization-guide.md §2): the SHAPE says what kind of thing a marker is, the icon its
    * type, the fill its date, the outline its tier — and the findings' icons, by what their source is. Shown whatever
    * layers are on: the findings always are.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def shapesHtml: Html =
    def badge(shape: String, inner: Html) =
      markup"""<span class="hd-badge hd-badge--$shape hd-key-badge">$inner</span>"""
    def bubble(id: String, form: BubbleForm) =
      DotCluster.bubbleHtml(
        DotCluster.Count(5),
        DotCluster.Size(20),
        (DotCluster.StartLabel("var(--accent)"), DotCluster.EndLabel("var(--accent)")),
        DotCluster.Id(id),
        DotCluster.ExtraClass("hd-key-cluster"),
        None,
        form
      )
    // the same icons as the toolbar's layer switches
    val shapes = PointKinds.all.map(k => Icon.html(k.icon) -> k.meaning.value) ++ List(
      Icon.html(Icon.Region) -> "a region or polity (its boundary)",
      bubble("hdb-key", BubbleForm.Round) -> "several nearby (click to list)",
      // the clusters of events keep the event's square (#1647)
      bubble("hdb-key-ev", BubbleForm.Events) -> "several events nearby",
      bubble("hdb-key-mix", BubbleForm.WithEvents) -> "several nearby, events among them"
    )
    val kinds = Source.Kinds.all.map(k =>
      badge("diamond", Glyphs.badge(CardIcons.sourceKind(k, marker = true))) ->
        (if k.value == Source.Kinds.Unknown then "source of unknown kind" else k.value)
    )
    def list(items: List[(Html, String)]) =
      Html.concat(items.map((m, label) => markup"""<li>$m<span>$label</span></li>"""))
    markup"""<div class="hd-key-group"><div class="hd-key-title">shapes</div><ul class="hd-key-list">${list(
        shapes
      )}</ul></div>""" ++
      markup"""<div class="hd-key-group"><div class="hd-key-title">findings, by source</div><ul class="hd-key-list">${list(
          kinds
        )}</ul></div>"""

  /** The key's markup: the shapes, then the icons of the layers on. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def html(events: Events, sites: Sites, undated: Undated = Undated.Hidden): Html =
    val body =
      shapesHtml ++ (if events.shown then eventsHtml else Html.empty) ++ (if sites.shown then sitesHtml else Html.empty)
    (if undated.shown then undatedHtml else Html.empty) ++
      markup"""<details class="hd-key"><summary>key</summary>$body</details>"""

  /** The legend's entry while the sites or events of no known date are drawn: their dashed outline. */
  val undatedHtml: Html =
    markup"""<div class="hd-legend-undated"><span class="hd-badge hd-badge--circle hd-key-badge hd-undated-swatch" aria-hidden="true"></span>""" ++
      markup"""<span>date unknown (dashed outline)</span></div>"""
