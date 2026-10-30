package com.histmersion.web.library

import com.histmersion.web.{LibrarySection, Router, StatusBadge}
import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** A page of a Library list as a table: a source's title and authors, year, status and period; an article's
  * title and kind, and period. Every title is a real link to the item's page: a plain click chooses the row
  * (the reading pane shows it), ⌘/Ctrl/middle click opens the page in a new tab, and Enter on a focused title
  * opens the page here. ↑ and ↓ move the choice while a title has the focus. */
object LibraryTable:

  private val colScope = htmlAttr("scope", com.raquo.laminar.codecs.StringAsIsCodec)

  /** Choose `key` in `section`'s list: a step in the history (`push`), or the arrow keys' quiet move. */
  type Choose = (String, Boolean) => Unit

  def element(section: LibrarySection, rows: Signal[List[TableRow]], chosen: Signal[Option[String]],
      choose: Choose): HtmlElement =
    val sources = section != LibrarySection.Articles
    div(
      cls := "library-table-wrap",
      onKeyDown --> { ev =>
        // Enter on a title opens its page here: the browsers do not agree on the click it would make
        val title = Option(ev.target.asInstanceOf[dom.Element].closest("a.lib-title"))
        if ev.key == "Enter" && !ev.altKey && !ev.metaKey && !ev.ctrlKey && !ev.shiftKey then
          title.foreach { a =>
            ev.preventDefault()
            dom.window.location.assign(a.getAttribute("href"))
          }
        TableKeys.delta(ev.key).foreach { d =>
          val keys = currentKeys(ev)
          if keys.nonEmpty && !ev.altKey && !ev.metaKey && !ev.ctrlKey then
            ev.preventDefault()
            TableKeys.step(keys, chosenKey(ev), d).foreach { k =>
              choose(k, false)
              focusRow(ev.currentTarget.asInstanceOf[dom.Element], k)
            }
        }
      },
      table(
        cls := "library-table",
        aria.label := (if sources then "Sources" else "Articles"),
        thead(tr(
          th(colScope := "col", "Title"),
          if sources then th(colScope := "col", cls := "col-year", "Year") else emptyNode,
          th(colScope := "col", cls := "col-status", if sources then "Status" else "Kind"),
          th(colScope := "col", cls := "col-period", "Period"))),
        tbody(children <-- rows.map(_.map(row(_, sources, chosen, choose))))
      )
    )

  private def currentKeys(ev: dom.KeyboardEvent): List[String] =
    val rows = ev.currentTarget.asInstanceOf[dom.Element].querySelectorAll("tbody tr[data-key]")
    (0 until rows.length).toList.map(i => rows(i).getAttribute("data-key"))

  private def chosenKey(ev: dom.KeyboardEvent): Option[String] =
    Option(ev.currentTarget.asInstanceOf[dom.Element].querySelector("tbody tr.is-chosen"))
      .map(_.getAttribute("data-key"))
      .orElse(Option(dom.document.activeElement).flatMap(el => Option(el.closest("tr[data-key]"))).map(_.getAttribute("data-key")))

  private def focusRow(wrap: dom.Element, key: String): Unit =
    val rows = wrap.querySelectorAll("tbody tr[data-key]")
    (0 until rows.length).map(rows(_)).find(_.getAttribute("data-key") == key).foreach { tr =>
      Option(tr.querySelector("a.lib-title")).foreach(_.asInstanceOf[dom.HTMLElement].focus())
    }

  private def row(r: TableRow, sources: Boolean, chosen: Signal[Option[String]], choose: Choose): HtmlElement =
    val on = chosen.map(_.contains(r.key))
    tr(
      cls := "lib-row",
      cls("is-chosen") <-- on,
      dataAttr("key") := r.key,
      aria.selected <-- on,
      // a click anywhere on the row chooses it, as on its title
      onClick --> { ev =>
        val t = ev.target.asInstanceOf[dom.Element]
        if t.closest("a") == null && Router.plain(ev) then choose(r.key, true)
      },
      td(cls := "col-title",
        a(cls := "lib-title", href := r.page, r.title,
          aria.current <-- on.map(if _ then "true" else "false"),
          onClick --> { ev =>
            if Router.plain(ev) then
              ev.preventDefault()
              choose(r.key, true)
          }),
        Option.when(r.byline.nonEmpty)(div(cls := "lib-byline", r.byline))),
      if sources then td(cls := "col-year", r.year.fold("")(_.toString)) else emptyNode,
      td(cls := "col-status", r.status.fold(span(cls := "lib-kind", r.kind))(StatusBadge(_))),
      td(cls := "col-period lib-period", r.period)
    )
