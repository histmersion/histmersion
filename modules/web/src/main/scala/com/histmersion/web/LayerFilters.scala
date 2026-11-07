package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.catalogue.{EventTaxonomy, EventTier, SiteTypes}

/** The events and sites layers' menus (#1449): fixed vocabularies — the kind taxonomy, listed under its two groups
  * (each group's "all" first), and the tiers; the site types. "all" is no filter. Each option shows what the map draws
  * for it: the kind's or the type's badge, the tier's filled or dashed square; "all" the layer's own icon. Pure option
  * lists.
  */
object LayerFilters:
  type Badge = Badge.T
  object Badge extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Glyph = Glyph.T
  object Glyph extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Icon = Icon.T
  object Icon extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TierMeaning = TierMeaning.T
  object TierMeaning extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** Pure: a badge of `shape` with `glyph`, as an option's icon (in the accent colour: no date). */

  def badge(shape: Shape, glyph: com.histmersion.web.LayerFilters.Glyph): com.histmersion.web.LayerFilters.Badge =
    s"""<span class="hd-badge hd-badge--${shape.cls} opt-badge">${Glyphs.badge(glyph)}</span>"""

  /** Pure: a layer's own switch icon, the "all" option's. */
  def layerIcon(k: PointKind[?]): com.histmersion.web.LayerFilters.Icon = Icon.html(k.icon)

  val eventKinds: List[IconSelect.Opt] =
    IconSelect.Opt(com.histmersion.web.IconSelect.Opt.Value(""), com.histmersion.web.IconSelect.Opt.Label("all"), layerIcon(PointKinds.Events)) :: EventTaxonomy.groups.flatMap { g =>
      val under = Some(s"$g events")
      IconSelect.Opt(g, s"all $g", layerIcon(PointKinds.Events), under) ::
        EventTaxonomy.kinds
          .filter(_.group == g)
          .map(k => IconSelect.Opt(k.kind, k.label, badge(Shape.Square, k.icon), under))
    }

  /** What each event tier means; the tier menu's tooltips. */
  def tierMeaning(tier: com.histmersion.api.Api.EventQuery.Tier): Option[com.histmersion.web.LayerFilters.TierMeaning] = tier match
    case EventTier.Attested =>
      Some("Events a source in the corpus attests: its own record, or an attestation linked to it.")
    case EventTier.Catalogue =>
      Some("Events only an external catalogue knows; no source in the corpus attests them yet.")
    case _ => None

  val tiers: List[IconSelect.Opt] = List(
    IconSelect.Opt(com.histmersion.web.IconSelect.Opt.Value(""), com.histmersion.web.IconSelect.Opt.Label("all"), Icon.html(Icon.TierPair), hint = Some("Every event, attested or not.")),
    IconSelect.Opt(
      EventTier.Attested,
      com.histmersion.web.IconSelect.Opt.Label("attested by a source"),
      """<span class="hd-badge hd-badge--square hd-ev hd-ev--attested opt-badge"></span>""",
      hint = tierMeaning(EventTier.Attested)
    ),
    IconSelect.Opt(
      EventTier.Catalogue,
      com.histmersion.web.IconSelect.Opt.Label("catalogue only"),
      """<span class="hd-badge hd-badge--square hd-ev hd-ev--catalogue opt-badge"></span>""",
      hint = tierMeaning(EventTier.Catalogue)
    )
  )
  val siteTypes: List[IconSelect.Opt] =
    IconSelect.Opt(com.histmersion.web.IconSelect.Opt.Value(""), com.histmersion.web.IconSelect.Opt.Label("all"), layerIcon(PointKinds.Sites)) :: SiteTypes.all.map(t =>
      IconSelect.Opt(t.id, t.label, badge(Shape.Circle, t.icon))
    )
