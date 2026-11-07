package com.histmersion.web.library

import com.histmersion.api.YearFmt
import com.histmersion.web.*
import com.histmersion.web.map.{GeoBounds, TheMap}
import com.histmersion.web.ui.Layout
import com.raquo.laminar.api.L.*

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.Failure
import com.histmersion.api.library.Finding
import com.histmersion.api.research.Article

/** Library's reading pane: the item chosen in the table. Its actions (show it on the map, open its page in a new tab,
  * pin it as a window; in a narrow window, back to the list), then what it is, its title, its authors, its status,
  * period and regions, its summary in reading type; a source's first findings and its links — its full text, what it
  * cites, who answers it, the articles that cite it; an article's opening and the sources it cites. What the table's
  * row holds is shown at once; the rest when the item is read.
  */
object ReadingPane:
  private val sourceReads = BoundedCache[String, Future[SourceReading]](BoundedCache.MaxEntries(Layout.libraryPage.value))
  private val articleReads = BoundedCache[String, Future[Article.View]](BoundedCache.MaxEntries(Layout.libraryPage.value))

  /** A failed read is forgotten, so choosing the item again tries again. */
  private def cached[A](cache: BoundedCache[String, Future[A]], key: String)(read: => Future[A]): Future[A] =
    cache.getOrElseUpdate(
      key, {
        val f = read
        f.onComplete {
          case Failure(_) => if cache.peek(key).contains(f) then { val _ = cache.remove(key) }; case _ => ()
        }
        f
      }
    )

  private def readSource(ck: com.histmersion.api.library.Citekey): Future[SourceReading] =
    cached(sourceReads, ck.value) {
      val record = ApiClient.getSource(ck, body = com.histmersion.api.Api.SourceViewQuery.FullText(false))
      val edges = ApiClient.neighbors(ck)
      val articles = ApiClient.sourceArticles(ck)
      for r <- record; e <- edges; a <- articles yield SourceReading(r, CitationCounts.of(e, a.size))
    }

  private def readArticle(slug: Article.Slug): Future[Article.View] =
    cached(articleReads, slug.value)(ApiClient.getArticle(slug, brief = Article.FullText(true)))

  def element(section: LibrarySection, chosen: Signal[Option[com.histmersion.api.personal.ItemKey]]): HtmlElement =
    htmlTag("aside")(
      cls := "reading-pane",
      aria.label := "Reading pane",
      child <-- chosen.distinct.map {
        case None =>
          div(
            cls := "reading-empty muted",
            p(if section == LibrarySection.Articles then "Choose an article to read it here."
            else "Choose a source to read it here."),
            p("↑ and ↓ move through the list; Enter opens the whole page.")
          )
        case Some(k) => if section == LibrarySection.Articles then articlePane(Article.Slug(k.value)) else sourcePane(com.histmersion.api.library.Citekey(k.value))
      }
    )

  // ── the actions ─────────────────────────────────────────────────────────────────────────────────

  private def actions(
      section: LibrarySection,
      page: String,
      pin: () => Unit,
      showOnMap: () => Unit,
      ref: Signal[marks.ItemRef],
      byline: String
  ): HtmlElement =
    div(
      cls := "reading-actions",
      child <-- ref.map(marks.ItemActions(_, byline)),
      button(
        typ := "button",
        cls := "btn btn--ghost btn--sm reading-back",
        "← Back to list",
        onClick --> (_ => Router.go(Route.Library(section)))
      ),
      div(
        cls := "btn-group reading-acts",
        a(
          cls := "btn btn--sm reading-map",
          href <-- UiState.signal.map(_ => explore),
          Icon(Icon.FoldedMap),
          "Show on the map",
          onClick --> { ev => if Router.plain(ev) then { ev.preventDefault(); showOnMap() } }
        ),
        a(
          cls := "btn btn--sm reading-tab",
          href := page,
          target := "_blank",
          rel := "noopener",
          Icon(Icon.NewTab),
          "Open in a new tab"
        ),
        button(
          typ := "button",
          cls := "btn btn--sm reading-pin",
          "Pin as window",
          title := "Keep it open in a window over the other pages",
          onClick --> (_ => pin())
        )
      )
    )

  /** Explore's address with the filters: what a modifier click on "Show on the map" opens. */
  private def explore: String =
    val hash = UiState.hashString
    Route.Explore.path + (if hash.value.nonEmpty then s"#$hash" else "")

  private def head(h: ReadingHead): HtmlElement =
    div(
      cls := "reading-head",
      Option.when(h.what.nonEmpty)(p(cls := "reading-what", h.what)),
      h1(cls := "reading-title", h.title),
      Option.when(h.authors.nonEmpty)(p(cls := "reading-authors", h.authors)),
      Option.when(h.status.isDefined || h.period.nonEmpty || h.regions.nonEmpty)(
        p(
          cls := "reading-facts",
          h.status.map(StatusBadge(_)),
          Option.when(h.period.nonEmpty)(span(cls := "lib-period", h.period)),
          Option.when(h.regions.nonEmpty)(span(cls := "reading-regions", h.regions))
        )
      ),
      h.summary.map(s => p(cls := "reading reading-summary", s))
    )

  // ── a source ────────────────────────────────────────────────────────────────────────────────────

  private def sourcePane(ck: com.histmersion.api.library.Citekey): HtmlElement =
    val row = LibraryLists.sources.now().rows.find(_.source.citekey.value == ck.value).map(_.source)
    val reading = Var(Option.empty[Either[com.histmersion.web.ErrorLine.Problem, SourceReading]])
    val page = SourceLink.href(ck)
    def showOnMap(): Unit =
      Router.go(Route.Explore)
      ResultsPanel.show(Preview.Target.Source(ck))
      reading
        .now()
        .flatMap(_.toOption)
        .flatMap(r => bounds(r.source.findings))
        .foreach(b => TheMap.surface.fitBounds(b, com.histmersion.web.map.MapSurface.FitBoundsPadding(40), com.histmersion.web.map.MapSurface.MaxZoom(Layout.fitMaxZoom.value)))
    val ref0 = marks.ItemRef.source(ck, row.map(_.title).getOrElse(com.histmersion.api.library.Title(ck.value)))
    // the item's own title once its record is read (the row is not on the page when the address chose it)
    val ref = reading.signal
      .map(_.flatMap(_.toOption).fold(ref0)(r => marks.ItemRef.source(ck, r.source.source.title)))
      .distinct
    val byline = row.fold("")(h =>
      List(TableRow.authors(h.authors.map(_.value)), h.year.map(_.value.toInt).fold("")(_.toString)).filter(_.nonEmpty).mkString(", ")
    )
    div(
      cls := "reading-item",
      dataAttr("key") := ck.value,
      com.histmersion.web.ui.ContextMenu
        .on(com.histmersion.web.ui.ContextMenu.OnTitle(s"Actions: ${ref0.title}"), () => marks.ItemMenu.entries(refNow(reading, ref0), byline)),
      onMountCallback { _ =>
        readSource(ck).onComplete { r =>
          reading.set(Some(r.toEither.left.map(message)))
          marks.MyItems.opened(refNow(reading, ref0))
        }
      },
      actions(
        LibrarySection.Sources,
        page,
        () => WindowManager.open(WindowKey.Source(ck)),
        () => showOnMap(),
        ref,
        byline
      ),
      div(
        cls := "reading-body",
        child <-- reading.signal.map {
          case Some(Right(r)) => head(ReadingHead.of(r.source))
          case _              => row.fold(p(cls := "muted", "Loading…"))(h => head(ReadingHead.of(h)))
        },
        child <-- reading.signal.map {
          case None           => emptyNode
          case Some(Left(m))  => ErrorLine.text(ErrorLine.Problem(s"Could not read this source: $m"))
          case Some(Right(r)) =>
            div(findings(ck, r.source.findings), marks.NoteList.on(ref0), sourceLinks(ck, r.counts))
        }
      )
    )

  /** The box around a source's located findings, for the map to show. Pure. */
  def bounds(findings: List[Finding.Row]): Option[GeoBounds] =
    val points = findings.flatMap(f => f.lat.zip(f.lon)).map((la, lo) => (la.value, lo.value))
    Option.when(points.nonEmpty)(
      GeoBounds(
        GeoBounds.South(points.map(_._1).min),
        GeoBounds.West(points.map(_._2).min),
        GeoBounds.North(points.map(_._1).max),
        GeoBounds.East(points.map(_._2).max)
      )
    )

  private def findings(ck: com.histmersion.web.WindowKey.Source.Citekey, all: List[Finding.Row]): Modifier[HtmlElement] =
    Option.when(all.nonEmpty)(
      div(
        cls := "reading-findings",
        h2(cls := "reading-subtitle", s"Findings · ${all.size}"),
        ul(all.take(Layout.readingFindings.value).map { f =>
          li(
            cls := "reading-finding",
            f.claim.value,
            Option.when(YearFmt.period(f.periodStart, f.periodEnd).nonEmpty)(
              span(cls := "lib-period", s" ${YearFmt.period(f.periodStart, f.periodEnd)}")
            ),
            f.regionName.filter(_.value.trim.nonEmpty).map(r => span(cls := "muted", s" · ${Fmt.display(com.histmersion.api.UiLabels.Text(r.value))}"))
          )
        }),
        Option.when(all.size > Layout.readingFindings.value)(
          a(cls := "reading-all-findings", href := s"${SourceLink.href(ck)}#findings", s"All ${all.size} findings")
        )
      )
    )

  private def countLink(href0: String, text: String, n: Int): HtmlElement =
    if n > 0 then a(href := href0, text) else span(cls := "reading-none", text)

  private def sourceLinks(ck: com.histmersion.api.library.Citekey, c: CitationCounts): HtmlElement =
    val page = SourceLink.href(ck)
    navTag(
      cls := "reading-links",
      aria.label := "More about this source",
      a(cls := "reading-full", href := s"$page?full=true", "Read the full text"),
      countLink(s"$page#cites", s"What it cites (${c.cites})", c.cites),
      countLink(s"$page#cited-by", s"Who answers it (${c.answers})", c.answers)
        .amend(title := "The sources that challenge, refute, update or supersede it"),
      countLink(
        s"$page#cited-by",
        if c.articles == 1 then "Cited in 1 article" else s"Cited in ${c.articles} articles",
        c.articles
      )
    )

  // ── an article ──────────────────────────────────────────────────────────────────────────────────

  private def articlePane(slug: Article.Slug): HtmlElement =
    val row = LibraryLists.articles.now().rows.find(_.slug == slug)
    val reading = Var(Option.empty[Either[com.histmersion.web.ErrorLine.Problem, Article.View]])
    val page = ArticleLink.href(slug)
    def showOnMap(): Unit =
      Router.go(Route.Explore)
      ResultsPanel.show(Preview.Target.Article(Preview.Target.Article.Slug(slug.value)))
    val ref0 = marks.ItemRef.article(slug, com.histmersion.api.library.Title(row.map(_.title.value).getOrElse(slug.value)))
    def refOf(v: Option[Either[com.histmersion.web.ErrorLine.Problem, Article.View]]) =
      v.flatMap(_.toOption).fold(ref0)(a => marks.ItemRef.article(slug, com.histmersion.api.library.Title(a.article.title.value)))
    val ref = reading.signal.map(refOf).distinct
    div(
      cls := "reading-item",
      dataAttr("key") := slug.value,
      com.histmersion.web.ui.ContextMenu
        .on(com.histmersion.web.ui.ContextMenu.OnTitle(s"Actions: ${ref0.title}"), () => marks.ItemMenu.entries(refOf(reading.now()), "")),
      onMountCallback { _ =>
        readArticle(slug).onComplete { r =>
          reading.set(Some(r.toEither.left.map(message)))
          marks.MyItems.opened(refOf(reading.now()))
        }
      },
      actions(
        LibrarySection.Articles,
        page,
        () => WindowManager.open(WindowKey.Article(slug)),
        () => showOnMap(),
        ref,
        ""
      ),
      div(
        cls := "reading-body",
        child <-- reading.signal.map {
          case Some(Right(v)) => head(ReadingHead.of(v))
          case _              => row.fold(p(cls := "muted", "Loading…"))(a => head(ReadingHead.of(a)))
        },
        child <-- reading.signal.map {
          case None           => emptyNode
          case Some(Left(m))  => ErrorLine.text(ErrorLine.Problem(s"Could not read this article: $m"))
          case Some(Right(v)) =>
            div(
              marks.NoteList.on(ref0),
              ArticleOpening
                .of(v.article.body, Layout.readingOpening)
                .filterNot(o => v.article.summary.exists(_.value.trim == o))
                .map(o => p(cls := "reading reading-opening", o)),
              navTag(
                cls := "reading-links",
                aria.label := "More about this article",
                a(cls := "reading-full", href := page, "Read the article"),
                countLink(s"$page#sources-cited", s"Sources cited (${v.sourcesCited.size})", v.sourcesCited.size)
              )
            )
        }
      )
    )

  private def refNow(reading: Var[Option[Either[com.histmersion.web.ErrorLine.Problem, SourceReading]]], ref0: marks.ItemRef): marks.ItemRef =
    reading
      .now()
      .flatMap(_.toOption)
      .fold(ref0)(r => marks.ItemRef.source(ref0.target.itemKey.map(k => com.histmersion.api.library.Citekey(k.value)).getOrElse(com.histmersion.api.library.Citekey("")), r.source.source.title))

  private def message(e: Throwable): com.histmersion.web.ErrorLine.Problem =
    com.histmersion.web.ErrorLine.Problem(Option(e.getMessage).filter(_.nonEmpty).getOrElse(e.toString))
