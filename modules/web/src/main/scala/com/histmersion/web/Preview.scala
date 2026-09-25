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
      summary: Option[String]
  )

  private val SummaryChars = 300

  def card(s: SourceDetail): Card =
    val ref = SourceRef(s.citekey, s.doi, s.authors, s.year, s.title, SourceStatus.fromString(s.status))
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

  // ── typed, cached fetches ────────────────────────────────────────────────────────────────────

  private val sourceCache  = scala.collection.mutable.Map.empty[String, Future[Card]]
  private val articleCache = scala.collection.mutable.Map.empty[String, Future[Card]]

  /** A failed fetch is forgotten, so the next hover tries again. */
  private def cached(cache: scala.collection.mutable.Map[String, Future[Card]], key: String)(
      fetch: => Future[Card]): Future[Card] =
    cache.getOrElseUpdate(key, {
      val f = fetch
      f.onComplete { case Failure(_) => cache.remove(key); case _ => () }
      f
    })

  def fetch(t: Target): Future[Card] = t match
    case Target.Source(ck)    => cached(sourceCache, ck)(ApiClient.getSource(ck, body = false).map(sw => card(sw.source)))
    case Target.Article(slug) => cached(articleCache, slug)(ApiClient.getArticle(slug).map(card))

  // ── the one handler ──────────────────────────────────────────────────────────────────────────

  private val Selector = "[data-ck],[data-slug]"

  /** The element the tooltip is currently for, and the tooltip itself. A single floating element,
    * re-rendered in place — only one preview is ever visible. */
  private var current: Option[dom.Element] = None
  private var tip: Option[(dom.HTMLElement, RootNode)] = None
  // `var`: installing twice (the app and a page entry point both call it) must not double-bind
  private var installed = false

  def install(): Unit =
    if !installed then
      installed = true
      document.addEventListener("mouseover", (e: dom.MouseEvent) => onOver(e))
      document.addEventListener("mouseout", (e: dom.MouseEvent) => onOut(e))
      document.addEventListener("scroll", (_: dom.Event) => hide(), useCapture = true)

  private def anchorOf(t: dom.EventTarget): Option[dom.Element] = t match
    case el: dom.Element => Option(el.closest(Selector))
    case _               => None

  private def onOver(e: dom.MouseEvent): Unit =
    anchorOf(e.target).filterNot(current.contains).foreach { el =>
      val t = targetOf(Option(el.getAttribute("data-ck")), Option(el.getAttribute("data-slug")))
      t.foreach(show(el, _))
    }

  private def onOut(e: dom.MouseEvent): Unit =
    current.foreach { el =>
      val to = Option(e.relatedTarget).collect { case n: dom.Node => n }
      if !to.exists(n => el.contains(n)) then hide()
    }

  private def show(el: dom.Element, t: Target): Unit =
    hide()
    current = Some(el)
    val box = document.createElement("div").asInstanceOf[dom.HTMLElement]
    box.className = "cite-tooltip"
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

  /** Under the link, flipped above it when it would leave the viewport. */
  private def place(anchor: dom.Element, box: dom.HTMLElement): Unit =
    val r    = anchor.getBoundingClientRect()
    val maxW = math.min(440.0, window.innerWidth - 20)
    box.style.maxWidth = s"${maxW}px"
    val left = math.max(10.0, math.min(r.left + r.width / 2 - maxW / 2, window.innerWidth - maxW - 10))
    box.style.left = s"${left}px"
    box.style.top = s"${r.bottom + 6}px"
    window.requestAnimationFrame { _ =>
      val b = box.getBoundingClientRect()
      if b.bottom > window.innerHeight - 10 then box.style.top = s"${r.top - b.height - 6}px"
    }

  private def cardView(c: Card): HtmlElement =
    div(
      div(cls := "cite-tip-title", c.title),
      Option.when(c.byline.nonEmpty)(div(cls := "cite-tip-meta", c.byline)),
      div(cls := "cite-tip-ident", c.identifier),
      Option.when(c.chips.nonEmpty)(div(cls := "cite-tip-chips",
        c.chips.map((field, v) => span(cls := "cite-chip", dataAttr("field") := field, Label.display(v))))),
      Option.when(c.where.nonEmpty)(div(cls := "cite-tip-geo", c.where)),
      c.status.map(s => div(cls := s"cite-tip-status ${s.toString.toLowerCase}", s.toString.toLowerCase)),
      c.summary.map(s => div(cls := "cite-tip-summary", s))
    )
