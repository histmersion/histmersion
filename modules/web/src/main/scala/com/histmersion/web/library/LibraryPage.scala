package com.histmersion.web.library

import com.histmersion.api.instance.UiConfig
import com.histmersion.web.*
import com.histmersion.web.ui.Layout
import com.raquo.laminar.api.L.*

/** Library: reading first. The filter panel on the left is Explore's own ([[FilterBar]], one filter state), with "My
  * library" above it; here the tabs (sources, articles, with their counts), the order, a page of the list as a table,
  * and the reading pane with the chosen item. The choice is in the address ([[LibraryAddress]]), so it survives a
  * reload and back/forward. In a narrow window the reading pane takes the whole width ("← Back to list"); in a phone's
  * the table's rows stack.
  */
object LibraryPage:

  val chosen: Signal[Option[com.histmersion.api.personal.ItemKey]] = Router.search.map(LibraryAddress.item).distinct

  /** Choose `key` in `section`'s list: a step in the history unless `push` is false. */
  def choose(section: LibrarySection)(key: com.histmersion.api.personal.ItemKey, push: Router.Push): Unit =
    Router.go(
      Route.Library(section),
      push = push,
      search = LibraryAddress.search(Some(key), LibraryAddress.shelf(Router.search.now()))
    )

  def element(ui: UiConfig, section: LibrarySection): HtmlElement =
    val articles = section == LibrarySection.Articles
    val page: Signal[ListPage[TableRow]] =
      if articles then LibraryLists.articles.signal.map(p => p.copy(rows = p.rows.map(TableRow.of)))
      else LibraryLists.sources.signal.map(p => p.copy(rows = p.rows.map(TableRow.of)))
    val noun = if articles then "articles" else "sources"
    div(
      cls := "library-page",
      cls(if articles then "library-articles-pane" else "library-sources-pane"),
      cls("has-item") <-- chosen.map(_.isDefined),
      onMountCallback(_ => LibraryLists.refresh()),
      UiState.signal.changes --> (_ => LibraryLists.refreshSoon()),
      div(
        cls := "library-list",
        div(
          cls := "library-head",
          navTag(
            cls := "library-tabs",
            aria.label := "What to list",
            tab(LibrarySection.Sources, "Sources", LibraryLists.sources.signal.map(_.total), section),
            tab(LibrarySection.Articles, "Articles", LibraryLists.articles.signal.map(_.total), section)
          ),
          div(cls := "library-head-end", filtersButton(ui), AddSources.menu)
        ),
        child <-- MyLibrary.shelf.map {
          case Shelf.Everything => everythingList(section, articles, page, noun)
          case s                => ShelfList.element(s, section, chosen)
        }
      ),
      ReadingPane.element(section, chosen)
    )

  private def everythingList(
      section: LibrarySection,
      articles: Boolean,
      page: Signal[ListPage[TableRow]],
      noun: String
  ): HtmlElement =
    div(
      cls := "library-everything",
      div(
        cls := "library-toolbar toolbar",
        span(cls := "library-range", role := "status", child.text <-- page.map(p => ListPage.rangeLine(p, noun))),
        if articles then span(cls := "library-order muted", "By kind, then title")
        else sortSelect,
        pager(section, page)
      ),
      child.maybe <-- page.map(_.error.map(m => ErrorLine.text(ErrorLine.Problem(s"Could not list the $noun: $m")))),
      LibraryTable.element(section, page.map(_.rows), chosen, choose(section)),
      child.maybe <-- page.map(listed =>
        Option.when(listed.loaded && listed.rows.isEmpty && listed.error.isEmpty)(
          p(
            cls := "library-empty muted",
            if articles then "No article cites the sources the filters select." else "No source matches the filters."
          )
        )
      )
    )

  private def tab(s: LibrarySection, label: String, total: Signal[com.histmersion.api.RowCount], on: LibrarySection): HtmlElement =
    a(
      cls := "library-tab",
      cls("on") := s == on,
      href <-- MyLibrary.shelf.map(sh => LibraryAddress.href(s, None, sh)),
      onClick --> { ev =>
        if Router.plain(ev) then
          ev.preventDefault()
          Router.go(Route.Library(s), search = LibraryAddress.search(None, LibraryAddress.shelf(Router.search.now())))
      },
      aria.current := (if s == on then "page" else "false"),
      label,
      span(cls := "tab-count", child.text <-- total.map(n => s" · ${ListPage.grouped(n.value)}"))
    )

  private def sortSelect: HtmlElement =
    label(
      cls := "library-sort",
      span(cls := "muted", "Sort "),
      select(
        cls := "field",
        idAttr := "library-sort",
        controlled(
          value <-- UiState.librarySort.signal.map(w => LibrarySort.of(w.map(LibrarySort.Wire(_))).wire.value),
          onChange.mapToValue --> { v =>
            UiState.set(UiState.librarySort := Option.when(v != LibrarySort.Relevance.wire.value)(v))()
          }
        ),
        LibrarySort.values.toList.map(s => option(value := s.wire.value, s.label.value))
      )
    )

  private def pager(section: LibrarySection, page: Signal[ListPage[TableRow]]): HtmlElement =
    div(
      cls := "btn-group library-pager",
      role := "group",
      aria.label := "Pages",
      button(
        typ := "button",
        cls := "btn btn--sm library-prev",
        "‹ Previous",
        disabled <-- page.map(!_.hasPrevious),
        onClick --> (_ => turn(section, back = true))
      ),
      button(
        typ := "button",
        cls := "btn btn--sm library-next",
        "Next ›",
        disabled <-- page.map(!_.hasNext),
        onClick --> (_ => turn(section, back = false))
      )
    )

  private def turn(section: LibrarySection, back: Boolean): Unit =
    val p = if section == LibrarySection.Articles then LibraryLists.articles.now() else LibraryLists.sources.now()
    LibraryLists.turnTo(
      section,
      if back then p.previousOffset(Layout.libraryPage.value) else p.nextOffset(Layout.libraryPage.value)
    )

  /** In the narrow layouts the filter panel is a drawer: this opens it, counting the active filters. */
  private def filtersButton(ui: UiConfig): HtmlElement =
    val active = UiState.signal.map(_ => FilterChips.countNow(ui))
    button(
      typ := "button",
      cls := "btn library-filters",
      idAttr := "library-filters",
      aria.expanded <-- MobileChrome.drawer.signal.map(_.contains(MobileChrome.Drawer.Filters)),
      Icon(Icon.Filter),
      "Filters",
      child.maybe <-- active.map(n => Option.when(n.value > 0)(span(cls := "rail-count", n.toString))),
      onClick --> (_ => MobileChrome.toggle(MobileChrome.Drawer.Filters))
    )
