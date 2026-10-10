package com.histmersion.web

import com.histmersion.core.*
import com.raquo.laminar.api.L.*
import org.scalajs.dom
import org.scalajs.dom.{document, window}

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Failure, Success}

/** THE link preview. One delegated hover handler on the document answers for every element that
  * carries `data-ck` (a source) or `data-slug` (an article) — server-rendered anchors in a window or
  * on a standalone page, and the web UI's own [[SourceLink]]s alike — so nothing has to be wired per
  * link, and a link added later previews too.
  *
  * Data comes TYPED through [[ApiClient]] (no DOM scraping, no `js.Dynamic`), once per id: the cache
  * holds the `Future`, so ten hovers over the same citation cost one request. */
object Preview:

  /** What an element asks to preview. */
  enum Target:
    case Source(citekey: String)
    case Article(slug: String)
    /** A gazetteer place on the map, with its hierarchy read as of `asOf` (the map's year). */
    case Place(id: String, asOf: Option[Long])
    /** A historical event (its row in the events list). */
    case Event(id: String)

  /** Pure: which preview the attributes ask for. A source wins when both are present. */
  def targetOf(ck: Option[String], slug: Option[String]): Option[Target] =
    ck.map(_.trim).filter(_.nonEmpty).map(Target.Source(_))
      .orElse(slug.map(_.trim).filter(_.nonEmpty).map(Target.Article(_)))

  /** The preview card of a source, as data — pure, so what it shows is a unit test. */
  final case class Card(
      title: String,
      byline: String,
      identifier: String,
      status: Option[SourceStatus],
      chips: List[(String, String)],
      where: String,
      summary: Option[String],
      /** Short facts, one per line (a place's parents and its counts). */
      facts: List[String] = Nil,
      /** Pages elsewhere, as (text, href): a place's Wikidata, GeoNames or Pleiades page. */
      links: List[(String, String)] = Nil,
      /** The picture a place or event card shows, with its credit and licence (#1446). */
      media: Option[MediaThumb] = None
  )

  private val SummaryChars = 300

  def card(s: SourceDetail): Card =
    val ref = SourceRef(s.citekey, s.doi, s.authors, s.year, s.title, s.status)
    Card(
      title = s.title,
      byline = List(s.authors.mkString(", "), s.year.fold("")(_.toString), s.venue.getOrElse(""))
        .filter(_.trim.nonEmpty).mkString(" · "),
      identifier = ref.idLabel,
      status = Some(ref.status),
      chips = s.topics.map("topic" -> _) ++ s.tags.map("tag" -> _),
      where = List(s.regions.map(Label.display).mkString(", "), YearFmt.period(s.periodStart, s.periodEnd))
        .filter(_.nonEmpty).mkString(" · "),
      summary = s.summary.map(_.trim).filter(_.nonEmpty)
        .map(t => if t.length > SummaryChars then t.take(SummaryChars - 1) + "…" else t))

  def card(a: ArticleView): Card =
    Card(a.article.title, Label.display(a.article.kind), a.article.slug, None, Nil,
      YearFmt.period(a.article.periodStart, a.article.periodEnd),
      a.article.summary.map(_.trim).filter(_.nonEmpty))

  private def plural(n: Int, one: String): String = s"$n $one${if n == 1 then "" else "s"}"

  /** Where a place comes from, in words: the external source that brought it in, else the seed's own
    * source line ("Pleiades (pleiades.stoa.org), CC-BY"). */
  private[web] def datasetOf(p: PlaceSummary): Option[String] =
    p.importSource.map { k =>
      if k.startsWith("lpf:") then s"LPF dataset ${k.drop(4)}" else PlaceLink.schemeTitle(k)
    }.orElse(p.source).map(_.trim).filter(_.nonEmpty)

  /** A place's card: what it is (name, kind, its period as the source states it — "end 1769 CE,
    * start unknown" — its description), whether a region has a boundary yet, where it comes from
    * with a link to that page, the places it is part of, and what the corpus holds for it (the counts
    * are what clicking it selects). */
  def card(d: PlaceDetail): Card =
    val p = d.place
    val contested = Option.when(d.contested && !p.label.toLowerCase.contains("contested"))("contested extent")
    val description = d.descriptions match
      case Nil => None
      case first :: rest =>
        val other = rest.find(_.lang != first.lang).map(o => s" · ${o.text}" + (if o.lang.nonEmpty then s" (${o.lang})" else ""))
        Some(first.text + other.getOrElse(""))
    val links = d.links.flatMap(l => PlaceLink.url(l.scheme, l.externalId).map(u =>
      s"${PlaceLink.schemeTitle(l.scheme)} ${l.externalId}" -> u)).distinct.take(4)
    Card(
      title = p.label,
      byline = (List(Label.display(p.kind).capitalize, p.when) ++ contested)
        .filter(_.nonEmpty).mkString(" · "),
      identifier = p.id,
      status = None,
      chips = Nil,
      where = if d.parents.isEmpty then "" else "part of " + d.parents.map(_.label).mkString(", "),
      summary = description,
      facts = Option.when(d.datedByFindings)(PlaceTimeBasis.FindingsNote).toList ++
        d.shapeNotes ++
        datasetOf(p).map(s => s"source: $s").toList ++
        d.counts.toList.map(c =>
          List(plural(c.sources, "source"), plural(c.findings, "finding"), plural(c.articles, "article"),
            plural(c.events, "event")).mkString(" · ")),
      links = links,
      media = d.media.headOption)

  /** An event's card: what it is, when, how sure, its description, and its picture (#1446). */
  def card(d: EventDetail): Card =
    val e = d.event
    val years = EventGeo.outer(e.timeSpan).fold("undated") { (a, b) =>
      if a == b then Fmt.yr(a) else s"${Fmt.yr(a)} – ${Fmt.yr(b)}"
    }
    Card(
      title = e.label,
      byline = List(Label.display(e.kind).capitalize, years, Label.display(e.historicity)).filter(_.nonEmpty).mkString(" · "),
      identifier = e.id,
      status = None,
      chips = Nil,
      where = d.parent.fold("")(p => s"part of ${p.label}"),
      summary = e.description.map(_.trim).filter(_.nonEmpty)
        .map(t => if t.length > SummaryChars then t.take(SummaryChars - 1) + "…" else t),
      links = d.wikidata.flatMap(q => PlaceLink.url("wikidata", q).map(u => s"Wikidata $q" -> u)).toList,
      media = d.media.headOption)

  /** Pure: the line under a card's picture — its credit and licence (the licence a link when it has a
    * page), as the licence asks for it to be shown. */
  def creditLine(t: MediaThumb): (String, Option[(String, String)]) =
    val lic = t.license.map(l => l -> t.licenseUrl.orElse(t.sourceUrl).getOrElse(""))
    (t.credit, lic.map((l, u) => (l, u)))

  // ── typed, cached fetches ────────────────────────────────────────────────────────────────────

  private val sourceCache  = scala.collection.mutable.Map.empty[String, Future[Card]]
  private val articleCache = scala.collection.mutable.Map.empty[String, Future[Card]]
  private val placeCache   = scala.collection.mutable.Map.empty[String, Future[Card]]
  private val eventCache   = scala.collection.mutable.Map.empty[String, Future[Card]]

  /** A failed fetch is forgotten, so the next hover tries again. */
  private def cached(cache: scala.collection.mutable.Map[String, Future[Card]], key: String)(
      fetch: => Future[Card]): Future[Card] =
    cache.getOrElseUpdate(key, {
      val f = fetch
      f.onComplete { case Failure(_) => cache.remove(key); case _ => () }
      f
    })

  /** Drop a source's cached card — it was just edited, and the next hover must show the edit. */
  def forget(ck: String): Unit = { val _ = sourceCache.remove(ck) }

  def fetch(t: Target): Future[Card] = t match
    case Target.Source(ck)    => cached(sourceCache, ck)(ApiClient.getSource(ck, body = false).map(sw => card(sw.source)))
    case Target.Article(slug) => cached(articleCache, slug)(ApiClient.getArticle(slug, brief = true).map(card))
    case Target.Place(id, asOf) =>
      cached(placeCache, s"$id@${asOf.getOrElse("")}")(ApiClient.place(id, asOf, counts = true).map(card))
    case Target.Event(id) => cached(eventCache, id)(ApiClient.getEvent(id).map(card))

  // ── the one handler ──────────────────────────────────────────────────────────────────────────

  private val Selector = "[data-ck],[data-slug],[data-place],[data-event]"

  /** The year a place chip's card reads the gazetteer at — the map's; set by whoever owns the map. */
  // `var`: the app's map sets it once it exists; a page without a map reads places as of now
  var placeAsOf: () => Option[Long] = () => None

  /** Pure: what an element asks to preview, a place chip's id included (a source or an article first). */
  def targetOf(ck: Option[String], slug: Option[String], place: Option[String], asOf: => Option[Long]): Option[Target] =
    targetOf(ck, slug).orElse(place.map(_.trim).filter(_.nonEmpty).map(Target.Place(_, asOf)))

  /** Pure: the same, an event's id last. */
  def targetOf(ck: Option[String], slug: Option[String], place: Option[String], event: Option[String],
      asOf: => Option[Long]): Option[Target] =
    targetOf(ck, slug, place, asOf).orElse(event.map(_.trim).filter(_.nonEmpty).map(Target.Event(_)))

  private def targetOfElement(el: dom.Element): Option[Target] =
    targetOf(Option(el.getAttribute("data-ck")), Option(el.getAttribute("data-slug")),
      Option(el.getAttribute("data-place")), Option(el.getAttribute("data-event")), placeAsOf())

  private def placeAnchorOf(t: dom.EventTarget): Option[dom.Element] = t match
    case el: dom.Element => Option(el.closest("[data-place]"))
    case _               => None

  /** The place chip a key or focus event is FOR — the chip itself, not its ✕ inside it. */
  private def chipItself(t: dom.EventTarget): Option[dom.Element] =
    placeAnchorOf(t).filter(el => t match { case n: dom.Element => n == el; case _ => false })

  /** A click on a place chip pins its card under it (its ✕ stops the click: removing is not reading). */
  private def onDocClick(e: dom.MouseEvent): Unit =
    placeAnchorOf(e.target) match
      case Some(el) if !e.defaultPrevented => pinUnder(el, keyboard = false)
      case _ =>
        // a click anywhere else in the page, away from the pinned card and what pinned it, lets it go
        // (a click on the map is the map's to judge: it may land on another place, [[PlaceHover]])
        val inside = e.target match
          case n: dom.Node => pinned.exists(p => p.box.contains(n) || p.owner.exists(_.contains(n))) ||
            n.isInstanceOf[dom.Element] && n.asInstanceOf[dom.Element].closest(".leaflet-container") != null
          case _ => false
        if !inside then unpin(restoreFocus = false)

  private def pinUnder(el: dom.Element, keyboard: Boolean): Unit =
    targetOfElement(el).foreach { t =>
      if isPinned(t) && !keyboard then unpin(restoreFocus = false)
      else
        pin(t, () => Option.when(el.isConnected) {
          val r = el.getBoundingClientRect()
          Anchor.Under(r.left, r.top, r.bottom, r.width)
        }, owner = Some(el), keyboard = keyboard)
    }

  /** The element the tooltip is currently for, and the tooltip itself. A single floating element,
    * re-rendered in place — only one preview is ever visible. */
  private var current: Option[dom.Element] = None
  private var tip: Option[(dom.HTMLElement, RootNode)] = None
  // `var`: installing twice (the app and a page entry point both call it) must not double-bind
  private var installed = false

  def install(): Unit =
    if !installed then
      installed = true
      // pointer events, so a TOUCH can be told apart: a tap follows the link, it does not float a card
      document.addEventListener("pointerover", (e: dom.PointerEvent) => if e.pointerType != "touch" then onOver(e))
      document.addEventListener("pointerout", (e: dom.PointerEvent) => onOut(e))
      document.addEventListener("scroll", (_: dom.Event) => { hide(); reposition() }, useCapture = true)
      window.addEventListener("resize", (_: dom.Event) => reposition())
      // the pinned card takes Escape first (the window manager's Escape runs after, on `window`)
      document.addEventListener("keydown", { (e: dom.KeyboardEvent) =>
        if PinRules.dismisses(e.key) && !e.defaultPrevented && pinned.isDefined then
          e.preventDefault()
          unpin()
      })
      // a place chip ([data-place]) answers like a place on the map: a click or Enter pins its card
      document.addEventListener("click", (e: dom.MouseEvent) => onDocClick(e))
      document.addEventListener("keydown", { (e: dom.KeyboardEvent) =>
        if PinRules.activates(e.key) then chipItself(e.target).foreach { el =>
          e.preventDefault()
          pinUnder(el, keyboard = true)
        }
      })
      document.addEventListener("focusin", (e: dom.FocusEvent) =>
        chipItself(e.target).filterNot(current.contains).foreach(el => targetOfElement(el).foreach(show(el, _))))
      document.addEventListener("focusout", (e: dom.FocusEvent) =>
        chipItself(e.target).filter(current.contains).foreach(_ => hide()))

  private def anchorOf(t: dom.EventTarget): Option[dom.Element] = t match
    case el: dom.Element => Option(el.closest(Selector))
    case _               => None

  private def onOver(e: dom.MouseEvent): Unit =
    anchorOf(e.target).filterNot(current.contains).foreach(el => targetOfElement(el).foreach(show(el, _)))

  private def onOut(e: dom.MouseEvent): Unit =
    current.foreach { el =>
      val to = Option(e.relatedTarget).collect { case n: dom.Node => n }
      if !to.exists(n => el.contains(n)) then hide()
    }

  private def show(el: dom.Element, t: Target): Unit =
    hide()
    if !isPinned(t) then showUnder(el, t)

  private def showUnder(el: dom.Element, t: Target): Unit =
    current = Some(el)
    val box = document.createElement("div").asInstanceOf[dom.HTMLElement]
    box.className = t match
      case _: Target.Place | _: Target.Event => "cite-tooltip place-tip"
      case _                                 => "cite-tooltip"
    document.body.appendChild(box)
    val content = Var[Option[Card]](None)
    val root = render(box, div(child <-- content.signal.map(_.fold(div(cls := "cite-tip-meta", "loading…"))(cardView))))
    tip = Some(box -> root)
    place(el, box)
    fetch(t).onComplete {
      case Success(c) if current.contains(el) =>
        content.set(Some(c))
        place(el, box)
      case Failure(_) if current.contains(el) => hide()
      case _                                  => ()
    }

  def hide(): Unit =
    tip.foreach { (box, root) => root.unmount(); box.remove() }
    tip = None
    current = None
    atPoint = None

  // ── a card at a point: the map's places, which are shapes rather than links ─────────────────────

  /** The target of the card floating at the pointer, if that is the card showing. */
  private var atPoint: Option[Target] = None

  /** Is the card at the pointer showing `t`? */
  def showingAt(t: Target): Boolean = atPoint.contains(t) && tip.isDefined

  /** Float `t`'s card beside the viewport point (`x`, `y`) — the same card, the same cache. A second
    * call for the target already showing only moves it. */
  def showAt(t: Target, x: Double, y: Double): Unit =
    if showingAt(t) then moveTo(x, y)
    else if isPinned(t) then hide()   // its card is already up, pinned: no second, moving copy
    else
      hide()
      atPoint = Some(t)
      val box = document.createElement("div").asInstanceOf[dom.HTMLElement]
      box.className = "cite-tooltip place-tip"
      box.setAttribute("role", "tooltip")
      document.body.appendChild(box)
      val content = Var[Option[Card]](None)
      val root = render(box, div(child <-- content.signal.map(_.fold(div(cls := "cite-tip-meta", "loading…"))(cardView))))
      tip = Some(box -> root)
      moveTo(x, y)
      fetch(t).onComplete {
        case Success(c) if atPoint.contains(t) =>
          content.set(Some(c))
          moveTo(x, y)
        case Failure(_) if atPoint.contains(t) => hide()
        case _                                 => ()
      }

  /** Hide the card at the pointer if it is `t`'s — a card for another target stays. */
  def hideAt(t: Target): Unit = if atPoint.contains(t) then hide()

  /** Beside the pointer, below and to the right; flipped to the other side when it would leave the
    * viewport. The position is data for `.cite-tooltip`. */
  def moveTo(x: Double, y: Double): Unit =
    tip.filter(_ => atPoint.isDefined).foreach { (box, _) =>
      val maxW = math.min(360.0, window.innerWidth - 20)
      box.style.setProperty("--tip-maxw", s"${maxW}px")
      val b = box.getBoundingClientRect()
      val (left, top) = pointPlacement(x, y, b.width, b.height, window.innerWidth, window.innerHeight)
      box.style.setProperty("--tip-x", s"${left}px")
      box.style.setProperty("--tip-y", s"${top}px")
    }

  /** Pure: where a `w`×`h` card goes for a pointer at (`x`, `y`) in a `vw`×`vh` viewport — 14 px
    * below-right of it, on the other side of the pointer when that edge would be crossed, and never
    * closer than 10 px to any edge. */
  def pointPlacement(x: Double, y: Double, w: Double, h: Double, vw: Double, vh: Double): (Double, Double) =
    val gap = 14.0
    val left = if x + gap + w > vw - 10 then x - gap - w else x + gap
    val top  = if y + gap + h > vh - 10 then y - gap - h else y + gap
    (math.max(10.0, math.min(left, vw - w - 10)), math.max(10.0, math.min(top, vh - h - 10)))

  /** Pure: where a `w`×`h` card goes under an element spanning (`left`, `top`)–(`left + width`,
    * `bottom`) in a `vw`×`vh` viewport — centred 6 px under it, above it when there is no room below,
    * and never closer than 10 px to a side. */
  def underPlacement(left: Double, top: Double, bottom: Double, width: Double, w: Double, h: Double,
      vw: Double, vh: Double): (Double, Double) =
    val x = math.max(10.0, math.min(left + width / 2 - w / 2, vw - w - 10))
    val y = if bottom + 6 + h > vh - 10 && top - 6 - h >= 10 then top - 6 - h else bottom + 6
    (x, y)

  // ── a PINNED card: a click (or Enter) keeps it still, so its links can be followed ──────────────

  /** Where a pinned card is anchored, as the viewport sees it now. */
  enum Anchor:
    /** Beside a point — a place on the map, where its hover card was. */
    case At(x: Double, y: Double)
    /** Under an element — a place chip — flipped above it when there is no room. */
    case Under(left: Double, top: Double, bottom: Double, width: Double)

  /** The pinned card: what it shows, its element, where it is anchored (`None` while the anchor is out
    * of view: the card waits, hidden), what pinned it (a click there is not "outside"), and where the
    * focus goes back to when it closes. */
  private final class Pinned(val target: Target, val box: dom.HTMLElement, val root: RootNode,
      val anchor: () => Option[Anchor], val owner: Option[dom.Element], val returnFocus: Option[dom.HTMLElement]):
    // `var`: the card's size, measured when its content arrives — a pan re-places it every frame
    // and must not force a layout each time
    var size: (Double, Double) = (0, 0)

  // `var`: at most one card is pinned
  private var pinned: Option[Pinned] = None

  def isPinned(t: Target): Boolean = pinned.exists(p => sameCard(p.target, t))

  /** Pure: do two targets show the same card? A place is the same place whichever layer read it, at
    * whichever year (the map's span and the filter round its middle differently). */
  def sameCard(a: Target, b: Target): Boolean = (a, b) match
    case (Target.Place(x, _), Target.Place(y, _)) => x == y
    case _                                        => a == b
  def pinnedTarget: Option[Target] = pinned.map(_.target)

  /** Pin `t`'s card at `anchor` — replacing any pinned card and the hover card. From the keyboard the
    * focus moves into the card (its ✕ first, then its links), and back to `owner` when it closes. */
  def pin(t: Target, anchor: () => Option[Anchor], owner: Option[dom.Element] = None, keyboard: Boolean = false): Unit =
    unpin(restoreFocus = false)
    hide()
    val box = document.createElement("div").asInstanceOf[dom.HTMLElement]
    box.className = "cite-tooltip place-tip is-pinned"
    box.setAttribute("role", "dialog")
    box.setAttribute("aria-label", "place")
    document.body.appendChild(box)
    val content = Var[Option[Card]](None)
    val close = button(typ := "button", cls := "btn btn--bare btn--icon btn--sm cite-tip-close",
      aria.label := "close the card", title := "Close (Escape)", "✕", onClick --> (_ => unpin()))
    val root = render(box, div(close,
      child <-- content.signal.map(_.fold(div(cls := "cite-tip-meta", "loading…"))(cardView))))
    val focusBack = if keyboard then owner.collect { case h: dom.HTMLElement => h } else None
    val p = Pinned(t, box, root, anchor, owner, focusBack)
    pinned = Some(p)
    measure(p)
    reposition()
    if keyboard then close.ref.focus()
    fetch(t).onComplete {
      case Success(c) if pinned.contains(p) =>
        content.set(Some(c))
        box.setAttribute("aria-label", c.title)
        measure(p)
        reposition()
      case Failure(_) if pinned.contains(p) => unpin(restoreFocus = false)
      case _                                => ()
    }

  /** Close the pinned card; from the keyboard, the focus returns to what pinned it. */
  def unpin(restoreFocus: Boolean = true): Unit =
    pinned.foreach { p =>
      pinned = None
      val hadFocus = Option(document.activeElement).exists(a => p.box.contains(a))
      p.root.unmount()
      p.box.remove()
      if restoreFocus || hadFocus then p.returnFocus.filter(_.isConnected).foreach(_.focus())
    }

  private def measure(p: Pinned): Unit =
    val maxW = math.min(360.0, window.innerWidth - 20)
    p.box.style.setProperty("--tip-maxw", s"${maxW}px")
    val b = p.box.getBoundingClientRect()
    p.size = (b.width, b.height)

  /** Keep the pinned card at its anchor — after a pan or zoom of the map, a scroll, a resize. */
  def reposition(): Unit =
    pinned.foreach { p =>
      p.anchor() match
        case None => p.box.classList.add("is-offscreen")
        case Some(a) =>
          p.box.classList.remove("is-offscreen")
          val (w, h) = p.size
          val (x, y) = a match
            case Anchor.At(ax, ay) => pointPlacement(ax, ay, w, h, window.innerWidth, window.innerHeight)
            case Anchor.Under(l, t, b, wd) => underPlacement(l, t, b, wd, w, h, window.innerWidth, window.innerHeight)
          p.box.style.setProperty("--tip-x", s"${x}px")
          p.box.style.setProperty("--tip-y", s"${y}px")
    }

  /** Under the link, flipped above it when it would leave the viewport. */
  private def place(anchor: dom.Element, box: dom.HTMLElement): Unit =
    val r    = anchor.getBoundingClientRect()
    val maxW = math.min(440.0, window.innerWidth - 20)
    // the position is data for `.cite-tooltip`, not a style of its own
    box.style.setProperty("--tip-maxw", s"${maxW}px")
    val left = math.max(10.0, math.min(r.left + r.width / 2 - maxW / 2, window.innerWidth - maxW - 10))
    box.style.setProperty("--tip-x", s"${left}px")
    box.style.setProperty("--tip-y", s"${r.bottom + 6}px")
    window.requestAnimationFrame { _ =>
      val b = box.getBoundingClientRect()
      if b.bottom > window.innerHeight - 10 then box.style.setProperty("--tip-y", s"${r.top - b.height - 6}px")
    }

  private def cardView(c: Card): HtmlElement =
    div(
      div(cls := "cite-tip-title", c.title),
      Option.when(c.byline.nonEmpty)(div(cls := "cite-tip-meta", c.byline)),
      c.media.map(mediaView),
      div(cls := "cite-tip-ident", c.identifier),
      Option.when(c.chips.nonEmpty)(div(cls := "cite-tip-chips",
        c.chips.map((_, v) => Chip.value(v)))),
      Option.when(c.where.nonEmpty)(div(cls := "cite-tip-geo", c.where)),
      c.status.map(s => div(cls := s"cite-tip-status ${s.toString.toLowerCase}", s.toString.toLowerCase)),
      c.facts.map(f => div(cls := "cite-tip-fact", f)),
      Option.when(c.links.nonEmpty)(div(cls := "cite-tip-links",
        c.links.map((t, href) => a(cls := "cite-tip-link", com.raquo.laminar.api.L.href := href,
          target := "_blank", rel := "noopener", t + " ↗")))),
      c.summary.map(s => div(cls := "cite-tip-summary", s))
    )

  /** A card's picture: the local thumbnail (never the provider's URL — a hover stays on this server),
    * and under it the credit line and the licence, linked to the licence's page. Its size is the
    * stored thumbnail's, as `--w`/`--h`, so the card does not jump when the image arrives. */
  private def mediaView(t: MediaThumb): HtmlElement =
    val (credit, lic) = creditLine(t)
    figure(
      cls := "cite-tip-media",
      onMountCallback(ctx => sizeOf(t).foreach { (w, h) =>
        ctx.thisNode.ref.style.setProperty("--w", w.toString)
        ctx.thisNode.ref.style.setProperty("--h", h.toString)
      }),
      img(cls := "cite-tip-thumb", src := t.url, alt := credit, loadingAttr := "lazy"),
      figCaption(cls := "cite-tip-credit",
        span(credit),
        lic.map((l, u) =>
          if u.isEmpty then span(" · ", l)
          else span(" · ", a(cls := "cite-tip-link", href := u, target := "_blank", rel := "noopener license", l))))
    )

  /** Pure: the thumbnail's stored size (`--w`/`--h` for `.cite-tip-media`), when it is known. */
  def sizeOf(t: MediaThumb): Option[(Int, Int)] = (t.width, t.height) match
    case (Some(w), Some(h)) if w > 0 && h > 0 => Some((w, h))
    case _                                    => None

  private val loadingAttr = htmlAttr("loading", com.raquo.laminar.codecs.StringAsIsCodec)
