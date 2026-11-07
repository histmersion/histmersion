package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.catalogue.{EventSummary, EventTaxonomy, EventTier, SiteTypes}

/** The events and sites layers' menus (#1449): fixed vocabularies — the kind taxonomy, listed under its two groups
  * (each group's "all" first), and the tiers; the site types. "all" is no filter. Each option shows what the map draws
  * for it: the kind's or the type's badge, the tier's filled or dashed square; "all" the layer's own icon. Pure option
  * lists.
  */
object LayerFilters:
  /** Pure: a badge of `shape` with `glyph`, as an option's icon (in the accent colour: no date). */
  def badge(shape: Shape, glyph: Glyphs.BadgeName): Icon.Html =
    Icon.Html.assume(s"""<span class="hd-badge hd-badge--${shape.cls} opt-badge">${Glyphs.badge(glyph)}</span>""")

  /** Pure: a layer's own switch icon, the "all" option's. */
  def layerIcon(k: PointKind[?]): Icon.Html = Icon.html(k.icon)

  val eventKinds: List[IconSelect.Opt] =
    IconSelect.Opt(IconSelect.Opt.Value(""), IconSelect.Opt.Label("all"), layerIcon(PointKinds.Events)) :: EventTaxonomy.groups.flatMap { g =>
      val under = Some(IconSelect.Opt.Group(s"$g events"))
      IconSelect.Opt(
        IconSelect.Opt.Value(g.value),
        IconSelect.Opt.Label(s"all $g"),
        layerIcon(PointKinds.Events),
        under
      ) ::
        EventTaxonomy.kinds
          .filter(_.group == g)
          .map(k =>
            IconSelect.Opt(
              IconSelect.Opt.Value(k.kind.value),
              IconSelect.Opt.Label(k.label.value),
              badge(Shape.Square, Glyphs.BadgeName(k.icon.value)),
              under
            )
          )
    }

  /** What each event tier means; the tier menu's tooltips. */
  def tierMeaning(tier: EventSummary.Tier): Option[IconSelect.Opt.Hint] = tier.value match
    case EventTier.Attested =>
      Some(IconSelect.Opt.Hint("Events a source in the corpus attests: its own record, or an attestation linked to it."))
    case EventTier.Catalogue =>
      Some(IconSelect.Opt.Hint("Events only an external catalogue knows; no source in the corpus attests them yet."))
    case _ => None

  val tiers: List[IconSelect.Opt] = List(
    IconSelect.Opt(
      IconSelect.Opt.Value(""),
      IconSelect.Opt.Label("all"),
      Icon.html(Icon.TierPair),
      hint = Some(IconSelect.Opt.Hint("Every event, attested or not."))
    ),
    IconSelect.Opt(
      IconSelect.Opt.Value(EventTier.Attested),
      IconSelect.Opt.Label("attested by a source"),
      Icon.Html.assume("""<span class="hd-badge hd-badge--square hd-ev hd-ev--attested opt-badge"></span>"""),
      hint = tierMeaning(EventSummary.Tier(EventTier.Attested))
    ),
    IconSelect.Opt(
      IconSelect.Opt.Value(EventTier.Catalogue),
      IconSelect.Opt.Label("catalogue only"),
      Icon.Html.assume("""<span class="hd-badge hd-badge--square hd-ev hd-ev--catalogue opt-badge"></span>"""),
      hint = tierMeaning(EventSummary.Tier(EventTier.Catalogue))
    )
  )
  val siteTypes: List[IconSelect.Opt] =
    IconSelect.Opt(IconSelect.Opt.Value(""), IconSelect.Opt.Label("all"), layerIcon(PointKinds.Sites)) :: SiteTypes.all.map(t =>
      IconSelect.Opt(
        IconSelect.Opt.Value(t.id.value),
        IconSelect.Opt.Label(t.label.value),
        badge(Shape.Circle, Glyphs.BadgeName(t.icon.value))
      )
    )
