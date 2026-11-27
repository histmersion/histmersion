package com.histmersion
package web

import hearth.kindlings.optics.syntax.*
import com.raquo.laminar.api.L.*
import com.histmersion.api.{Api, Plural, Wording}
import com.histmersion.web.ui.Layout
import org.scalajs.dom

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.research.{ReviewBadge, ReviewState}
import com.histmersion.api.research.Article
import com.histmersion.api.catalogue.Year
import com.histmersion.api.RowCount
import com.histmersion.web.marks.RowActions
import com.histmersion.web.marks.ItemRef
import com.histmersion.api.library.Title
import com.histmersion.api.UiLabels
import com.histmersion.api.Markdown
import com.histmersion.lint.{tolerated, LintRule}

object ArticlesTab:

  /** The tab ([[FilterTab]]): the GLOBAL filter (title included), as the Sources tab sends it — the server relates it
    * to articles (see [[Api.ArticleQuery]]), so the two lists cannot disagree about what is filtered. A page at a time;
    * "show more" fetches past the first.
    */

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val tab = FilterTab[Api.ArticleQuery, Article.Summary](
    name = FilterTab.Name("articles"),
    singular = Plural.Noun.Singular("article"),
    noun = Plural.Noun.Plural("articles"),
    query = () => UiState.articleQuery(),
    fetch = (q, offset) =>
      ApiClient
        .articles(q.modify(_.filter.offset).setTo(offset))
        .map(r =>
          TabPage(
            r.articles,
            r.total,
            kindCounts(r.kinds).map(TabPage.Extras(_)) :+ TabPage
              .Extras(ResultsPaging.span(UiState.from.now().map(Year(_)), UiState.to.now().map(Year(_))).value)
          )
        ),
    render = renderArticle(_, inPanel = true),
    empty = FilterTab.Empty("No articles relate to the current filter."),
    page = ResultsPaging.PageNumber(Layout.tabPage.value),
    debounceMs = () => UiState.reloadDelay(UiState.ReloadDelayNormalMs(Layout.tabReloadMs.value))
  )

  def pane: HtmlElement = tab.pane

  /** How many the list holds, once it has an answer (its tab's count). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val total: Signal[Option[RowCount]] =
    tab.state.signal.map(s => Option.when(s.loaded.loaded)(RowCount(math.max(s.total.value, s.rows.size))))

  /** An article's row: its link (in Explore's results a plain click shows its detail there; elsewhere it opens its
    * window) and "open in a new tab".
    */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[web] def renderArticle(art: Article.Summary, inPanel: Boolean = false): HtmlElement =
    li(
      cls := "article-row",
      RowActions(
        ItemRef.article(art.slug, Title(art.title.value)),
        ItemRef.Href(Route.Article(Route.Article.Slug(art.slug.value)).path),
        Fmt.display(UiLabels.Text(art.kind.value)).value
      ),
      a(
        href := Route.Article(Route.Article.Slug(art.slug.value)).path,
        cls := "article-link",
        onClick --> { ev =>
          if Router.plain(ev) then
            ev.preventDefault()
            if inPanel then ResultsPanel.show(Preview.Target.Article(Preview.Target.Article.Slug(art.slug.value)))
            else ArticleModal.openSlug(art.slug)
        },
        div(cls := "article-title", art.title),
        div(
          cls := "article-meta",
          span(cls := "article-kind", Fmt.display(UiLabels.Text(art.kind.value))),
          art.review.map(reviewBadge),
          Fmt.periodStr(art.periodStart, art.periodEnd).value match
            case ""  => emptyNode
            case per => span(cls := "article-period", per)
        ),
        art.summary
          .filter(_.value.nonEmpty)
          .map { s =>
            div(cls := "article-summary", Fmt.clip(stripMd(s.value), Markdown.CharLimit(120)))
          }
          .getOrElse(emptyNode),
        if art.tags.nonEmpty then
          div(cls := "article-tags", art.tags.filter(_.value.nonEmpty).map(t => Chip.value(Chip.Choice(t.value))))
        else emptyNode
      )
    )

  /** The count line's per-kind counts: `200 concepts`, `3 indexes`, the largest first. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def kindCounts(kinds: List[Article.KindCount]): List[String] =
    Article.List
      .byKind(kinds)
      .map(k =>
        Plural
          .count(
            Plural.CountN(k.count.value.toLong),
            Plural.CountSingular(k.kind.value),
            Plural.CountPlural(if k.kind.value.endsWith("x") then k.kind.value + "es" else "")
          )
          .value
      )

  /** An article's review state as a coloured badge, what it means on hover. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def reviewBadge(b: ReviewBadge): HtmlElement =
    span(cls := reviewClass(b.state), title := b.detail, Wording.capitalize(b.label.value))

  /** The badge's classes: the stylesheet colours each state (as the article page's badge). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def reviewClass(state: ReviewState): String = s"job-state review-state state-${state.wire}"

  private def stripMd(s: String): String =
    s.replaceAll("\\[\\[([^\\]]+)\\]\\]", "$1")
      .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
      .replaceAll("\\*([^*]+)\\*", "$1")
      .replaceAll("#+ ", "")
      .replaceAll("\\s+", " ")
      .trim

  def show(tabName: String): Unit =
    if tabName == "articles" then
      UiState.set(UiState.tab := Some("articles"))()
      load()

  def restore(): Unit =
    UiState.tab.now() match
      case Some("articles") => load()
      case _                => ()

  /** Called on every UiState change: refetch only when the query this tab sends has changed. */
  def refresh(): Unit = tab.refresh()

  def load(): Unit = tab.load()
