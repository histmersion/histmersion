package com.histmersion.web

import com.histmersion.core.*
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*

import scala.scalajs.js

/** The web UI's shared building blocks. Each thing the UI shows in more than one place — a source
  * link, a chip, a confidence mark, a layer switch, an icon — is built HERE, once, so the sidebar,
  * the tabs, the map popup and the graph cannot drift into five looks and three behaviours. The
  * server's pages use the same class names and attributes (`server.HtmlRender`). */

/** Inline SVG icons drawn with `currentColor`, so they follow the button's text colour (and its
  * active state) instead of the platform's emoji font. 24×24 stroke paths. */
enum Icon(val paths: List[String]):
  case Boundaries extends Icon(List("M12 2.5l8.2 4.75v9.5L12 21.5l-8.2-4.75v-9.5z"))
  case Sites      extends Icon(List("M3 20.5h18", "M5.5 20.5v-9", "M10 20.5v-9", "M14 20.5v-9", "M18.5 20.5v-9",
                                    "M2.5 9.5h19L12 3.5z"))
  case Media      extends Icon(List("M3.5 5h17v14h-17z", "M3.5 16.5l5-5 4 4 2.5-2.5 5.5 5.5",
                                    "M9.5 9a1.5 1.5 0 1 1-3 0 1.5 1.5 0 0 1 3 0z"))
  case InView     extends Icon(List("M4 9V4h5", "M15 4h5v5", "M20 15v5h-5", "M9 20H4v-5", "M11 12h2"))

object Icon:
  /** The icon as a Laminar SVG element. */
  def apply(i: Icon): SvgElement =
    svg.svg(
      svg.cls := "icon",
      svg.viewBox := "0 0 24 24",
      svg.width := "14",
      svg.height := "14",
      svg.fill := "none",
      svg.stroke := "currentColor",
      svg.strokeWidth := "2",
      svg.strokeLineCap := "round",
      svg.strokeLineJoin := "round",
      aria.hidden := true,
      i.paths.map(d => svg.path(svg.d := d))
    )

/** An overlay the map can show. The wire name is how it is persisted in [[UiState]]. */
enum MapLayer(val wire: String, val label: String, val icon: Icon):
  case Boundaries extends MapLayer("boundaries", "boundaries", Icon.Boundaries)
  case Sites      extends MapLayer("sites", "sites", Icon.Sites)
  case Media      extends MapLayer("media", "media", Icon.Media)

object MapLayer:
  def fromWire(s: String): Option[MapLayer] = values.find(_.wire == s.trim.toLowerCase)

/** A toggle BUTTON (never a checkbox) whose pressed state is a signal: it cannot disagree with the
  * thing it switches, because it shows that thing's state rather than keeping its own. */
object LayerToggle:
  def apply(icon: Icon, label: String, on: Signal[Boolean], set: Boolean => Unit): HtmlElement =
    button(
      typ := "button",
      cls := "layer-btn",
      cls("active") <-- on,
      aria.pressed <-- on.map(_.toString),
      title := label,
      Icon(icon),
      span(cls := "layer-label", label),
      onClick.compose(_.sample(on)) --> (current => set(!current))
    )

/** The one source link: `<a class="src-ref" data-ck data-doi href="/source/ck">`, the shape the
  * server renders too. Hovering previews it ([[Preview]]'s delegated handler); clicking opens the
  * source in a window (plain navigation with a modifier key, as any link). */
object SourceLink:

  /** The identifier label: the DOI when there is one, else our citekey (the owner's rule). */
  def idLabel(citekey: String, doi: Option[String]): String =
    doi.map(_.trim).filter(_.nonEmpty).getOrElse(citekey)

  def href(citekey: String): String = s"/source/${HtmlAtoms.segment(citekey)}"

  def apply(citekey: String, doi: Option[String] = None, label: Option[String] = None): HtmlElement =
    a(
      cls := "src-ref",
      dataAttr("ck") := citekey,
      doi.filter(_.trim.nonEmpty).map(d => dataAttr("doi") := d),
      L.href := href(citekey),
      label.getOrElse(idLabel(citekey, doi)),
      onClick --> { ev =>
        if !ev.metaKey && !ev.ctrlKey && !ev.shiftKey then
          ev.preventDefault()
          ev.stopPropagation()
          ArticleModal.open(citekey, UiState.q.now().map("q" -> _).toMap)
      }
    )

  /** The same anchor as an HTML string, for Leaflet popups (which take markup, not elements). */
  def html(citekey: String, doi: Option[String], label: String): String =
    HtmlAtoms.sourceLink(citekey, doi, label)

/** The one chip: a readable label (never an underscore — [[Label.display]]), an optional prefix,
  * and an optional remove button. Editable chip inputs, the empty-state list, article cards and the
  * link preview all draw theirs with it. */
object Chip:
  def apply(
      text: String,
      remove: Option[() => Unit] = None,
      prefix: Option[String] = None,
      tip: Option[String] = None,
      extraCls: String = "",
      mods: Modifier[HtmlElement]*
  ): HtmlElement =
    span(
      cls := s"chip $extraCls".trim,
      tip.map(title := _),
      prefix.map(p => span(cls := "pfx", p)),
      text,
      mods,
      remove.map(r =>
        button(typ := "button", cls := "chip-x", aria.label := s"remove $text", "✕",
          onClick.stopPropagation --> (_ => r())))
    )

  /** A chip for a stored VALUE: the label is its display form. */
  def value(v: String, remove: Option[() => Unit] = None, prefix: Option[String] = None): HtmlElement =
    apply(Label.display(v), remove, prefix)

/** The confidence mark: the shared icon plus the colour the map's dots use for that level
  * ([[ConfidenceStyle]]), labelled for screen readers and on hover. */
object ConfidenceBadge:
  def apply(c: Confidence): HtmlElement =
    span(
      cls := s"conf-badge conf-${c.toString.toLowerCase}",
      styleAttr := s"color:${ConfidenceStyle.color(c)}",
      title := ConfidenceStyle.label(c),
      svg.svg(
        svg.cls := "conf-icon", svg.width := "10", svg.height := "10", svg.viewBox := ConfidenceStyle.IconViewBox,
        aria.hidden := true,
        ConfidenceStyle.iconParts(c).map(p =>
          if p.filled then svg.path(svg.d := p.d, svg.fill := "currentColor")
          else svg.path(svg.d := p.d, svg.fill := "none", svg.stroke := "currentColor"))
      ),
      span(cls := "conf-text", c.toString.toLowerCase)
    )

  /** As an HTML string, for Leaflet popups — the server's markup, from [[HtmlAtoms]]. */
  def html(c: Confidence): String = HtmlAtoms.confidenceBadge(c)
