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
  /** Stacked sheets: the map's layers (and, on a phone, the drawer that holds them). */
  case Layers     extends Icon(List("M12 3.5l8.5 4.5-8.5 4.5-8.5-4.5z", "M3.5 12l8.5 4.5 8.5-4.5", "M3.5 16l8.5 4.5 8.5-4.5"))
  /** A marker with its halo: the finding markers ("sources") on the map. */
  case Sources    extends Icon(List("M12 9a3 3 0 1 1 0 6 3 3 0 0 1 0-6z", "M12 3.5a8.5 8.5 0 1 1 0 17 8.5 8.5 0 0 1 0-17z"))
  case Boundaries extends Icon(List("M12 2.5l8.2 4.75v9.5L12 21.5l-8.2-4.75v-9.5z"))
  case Sites      extends Icon(List("M3 20.5h18", "M5.5 20.5v-9", "M10 20.5v-9", "M14 20.5v-9", "M18.5 20.5v-9",
                                    "M2.5 9.5h19L12 3.5z"))
  case Media      extends Icon(List("M3.5 5h17v14h-17z", "M3.5 16.5l5-5 4 4 2.5-2.5 5.5 5.5",
                                    "M9.5 9a1.5 1.5 0 1 1-3 0 1.5 1.5 0 0 1 3 0z"))
  case InView     extends Icon(List("M4 9V4h5", "M15 4h5v5", "M20 15v5h-5", "M9 20H4v-5", "M11 12h2"))
  case Jobs       extends Icon(List("M12 3a9 9 0 1 0 9 9", "M12 7v5l3 2", "M21 3v5h-5"))
  case Upload     extends Icon(List("M12 16V4", "M7 9l5-5 5 5", "M4 16v4h16v-4"))
  case Acquire    extends Icon(List("M5 3.5h11l3 3v14H5z", "M9 9h6", "M9 13h6", "M9 17h4"))
  /** A checklist: the backlog. */
  case Tasks      extends Icon(List("M4 6.5l1.5 1.5 3-3", "M4 12.5l1.5 1.5 3-3", "M4 18.5l1.5 1.5 3-3",
                                    "M12 7h8", "M12 13h8", "M12 19h8"))
  /** A bound notebook: the worklog. */
  case Log        extends Icon(List("M6 3.5h12v17H6z", "M9.5 3.5v17", "M13 8h2.5", "M13 12h2.5"))
  /** An i in a circle: about this server. */
  case Info       extends Icon(List("M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18z", "M12 11v6", "M12 7.5h.01"))
  case Key        extends Icon(List("M11 14a4.5 4.5 0 1 1 2-2l8 8v2h-3v-2h-2v-2h-2z", "M7.5 9.5h.01"))
  case Edit       extends Icon(List("M4 20h4L19 9l-4-4L4 16z", "M13.5 6.5l4 4"))
  /** A gear with a spark: the embedded agent runs. */
  case Runs       extends Icon(List("M12 8.5a3.5 3.5 0 1 1 0 7 3.5 3.5 0 0 1 0-7z", "M12 2.5v3", "M12 18.5v3",
                                    "M2.5 12h3", "M18.5 12h3", "M5.3 5.3l2.1 2.1", "M16.6 16.6l2.1 2.1",
                                    "M5.3 18.7l2.1-2.1", "M16.6 7.4l2.1-2.1"))
  /** A tick in a box: proposals waiting for a decision. */
  case Approvals  extends Icon(List("M4 4h16v16H4z", "M8 12.5l3 3 5-6"))
  /** A target: the research goals. */
  case Goals      extends Icon(List("M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18z", "M12 7.5a4.5 4.5 0 1 1 0 9 4.5 4.5 0 0 1 0-9z",
                                    "M12 11.5a.5.5 0 1 1 0 1 .5.5 0 0 1 0-1z"))
  /** Two paths joining: merging another collection into this one. */
  case Merge      extends Icon(List("M6 3.5v4a5 5 0 0 0 5 5h1a5 5 0 0 1 5 5v3", "M18 3.5v4a5 5 0 0 1-5 5",
                                    "M14 17.5l3 3 3-3"))
  /** A bolt: the events layer (#1449). */
  case Events     extends Icon(List("M13 2 3 14h9l-1 8 10-12h-9l1-8z"))
  case Graph      extends Icon(List("M6 6a2 2 0 1 0 0 .01", "M18 8a2 2 0 1 0 0 .01", "M12 18a2 2 0 1 0 0 .01",
                                    "M7.7 7.2l8.6 1.3", "M7 8l4 8.3", "M17 10l-4.2 6.4"))

object Icon:
  /** The icon as a Laminar SVG element. */
  def apply(i: Icon): SvgElement =
    svg.svg(
      svg.cls := "icon",   // size, stroke and fill: `.icon` in the stylesheet
      svg.viewBox := "0 0 24 24",
      aria.hidden := true,
      i.paths.map(d => svg.path(svg.d := d))
    )

/** An overlay the map can show. The wire name is how it is persisted in [[UiState]]. */
enum MapLayer(val wire: String, val label: String, val icon: Icon):
  case Boundaries extends MapLayer("boundaries", "boundaries", Icon.Boundaries)
  case Sites      extends MapLayer("sites", "sites", Icon.Sites)
  case Media      extends MapLayer("media", "media", Icon.Media)
  case Events     extends MapLayer("events", "events", Icon.Events)

object MapLayer:
  def fromWire(s: String): Option[MapLayer] = values.find(_.wire == s.trim.toLowerCase)

/** A toggle BUTTON (never a checkbox) whose pressed state is a signal: it cannot disagree with the
  * thing it switches, because it shows that thing's state rather than keeping its own. */
object LayerToggle:
  def apply(icon: Icon, label: String, on: Signal[Boolean], set: Boolean => Unit): HtmlElement =
    button(
      typ := "button",
      cls := "btn btn--toggle",   // the shared control look: style.css `.btn`, `.btn--toggle`
      aria.pressed <-- on.map(_.toString),
      title := label,
      Icon(icon),
      span(label),
      onClick.compose(_.sample(on)) --> (current => set(!current))
    )

/** A button that OPENS A WINDOW, drawn like a [[LayerToggle]]: pressed while its window is open,
  * because that is read from [[UiState.open]] rather than kept here. A second click closes it. */
object WindowToggle:
  def apply(icon: Icon, label: String, key: WindowKey): HtmlElement =
    LayerToggle(icon, label, UiState.open.signal.map(_.contains(key)),
      on => if on then WindowManager.open(key) else WindowManager.close(key))

/** A button that starts a WRITE. While the server wants an API key and none is held it is drawn
  * locked and opens the key window instead ([[Auth]]) — it asks for the key rather than failing. */
object WriteButton:
  def apply(icon: Icon, label: String, tip: String, action: () => Unit): HtmlElement =
    button(
      typ := "button",
      cls := "btn btn--ghost",
      cls("locked") <-- Auth.writable.map(!_),
      title <-- Auth.writable.map(w => if w then tip else s"$tip — enter the API key first"),
      Icon(icon),
      span(label),
      onClick --> { _ => if Auth.writableNow then action() else WindowManager.open(WindowKey.ApiKey) }
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

/** The one article link: `<a class="art-ref" data-slug href="/article/slug">` — the shape the server
  * renders ([[HtmlAtoms.articleLink]]), so [[Preview]] previews it the same everywhere. */
object ArticleLink:
  def href(slug: String): String = s"/article/${HtmlAtoms.segment(slug)}"

  def apply(slug: String, label: String): HtmlElement =
    a(
      cls := "art-ref",
      dataAttr("slug") := slug,
      L.href := href(slug),
      label,
      onClick --> { ev =>
        if !ev.metaKey && !ev.ctrlKey && !ev.shiftKey then
          ev.preventDefault()
          ev.stopPropagation()
          ArticleModal.openSlug(slug)
      }
    )

/** A bare corpus identifier met in free text (`cline2014_year`, `achilles_origins`): shown readable at
  * once, then — when it names a stored source or article — upgraded to that link ("Cline 2014", the
  * article's title). Lookups are cached; an identifier that names nothing stays readable text. */
object IdentLink:
  enum Resolved:
    case Source(ref: SourceRef)
    case Article(slug: String, title: String)

  private val cache = scala.collection.mutable.Map.empty[String, scala.concurrent.Future[Option[Resolved]]]

  private def resolve(id: String): scala.concurrent.Future[Option[Resolved]] =
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
    cache.getOrElseUpdate(id,
      ApiClient.resolveId(id)
        .map(r => r.source.map(s => Resolved.Source(s.ref)).orElse(r.article.map(a => Resolved.Article(a.slug, a.title))))
        .recover { case _ => None })

  def apply(id: String): HtmlElement =
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
    span(
      cls := "ident",
      child <-- Signal.fromFuture(resolve(id)).map {
        case Some(Some(Resolved.Source(r)))        => SourceLink(r.citekey, r.doi, Some(r.shortCite))
        case Some(Some(Resolved.Article(sl, tit))) => ArticleLink(sl, tit)
        case _                                     => span(Label.display(id))
      }
    )

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
      cls := s"conf-badge conf-${c.toString.toLowerCase}",   // its colour: `.conf-badge.conf-*`
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
