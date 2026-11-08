package com.histmersion
package web.library

import com.histmersion.api.personal.{ItemKey, Target, UnreadPage}
import com.histmersion.api.ItemKind
import com.histmersion.web.*
import com.histmersion.web.marks.{ItemRef, MyItems, NoteEditor, OpenBack}
import com.histmersion.web.ui.Layout
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.Paging
import com.histmersion.api.Offset

/** A shelf of My library in place of the table: the person's bookmarks, notes, what they have not marked read (the
  * tab's sources or articles, a page at a time) or what they opened lately. A row opens back where it was left — a
  * source or an article in the reading pane beside it, anything else where it lives — and a dot marks a source or an
  * article not marked read.
  */
object ShelfList:
  private def isRead(i: Target.Identity): Boolean = MyItems.readNow(i)

  /** The section a source's or an article's row is read in. */
  private def sectionOf(k: ItemKind): Option[LibrarySection] = k match
    case ItemKind.Source  => Some(LibrarySection.Sources)
    case ItemKind.Article => Some(LibrarySection.Articles)
    case _                => None

  private def open(r: ShelfRow, shelf: Shelf, section: LibrarySection): Unit =
    (sectionOf(r.target.kind), r.target.itemKey) match
      case (Some(s), Some(k)) => Router.go(Route.Library(s), search = LibraryAddress.search(Some(k), shelf))
      case _                  => OpenBack(r.target)

  private def hrefOf(r: ShelfRow, shelf: Shelf): String =
    (sectionOf(r.target.kind), r.target.itemKey) match
      case (Some(s), Some(k)) => LibraryAddress.href(s, Some(k), shelf)
      case _                  => ItemRef.back(r.target).value

  def element(shelf: Shelf, section: LibrarySection, chosen: Signal[Option[ItemKey]]): HtmlElement =
    val unread = Var(Option.empty[Either[String, UnreadPage]])
    val offset = Var(0)
    def readUnread(): Unit =
      val kind = if section == LibrarySection.Articles then ItemKind.Article else ItemKind.Source
      ApiClient
        .unread(kind.wire, Paging.Limit(Layout.libraryPage.value), Offset(offset.now()))
        .onComplete(r =>
          unread.set(Some(r.toEither.left.map(e => Option(e.getMessage).getOrElse("the server could not be reached"))))
        )
    val rows: Signal[List[ShelfRow]] = shelf match
      case Shelf.Bookmarks =>
        MyItems.bookmarks.signal
          .combineWith(MyItems.marks.signal)
          .map((bs, _) => bs.map(ShelfRow.of(_, isRead, ShelfRow.defaultAgo)))
      case Shelf.Notes =>
        MyItems.notes.signal
          .combineWith(MyItems.marks.signal)
          .map((ns, _) => ns.map(ShelfRow.of(_, isRead, ShelfRow.defaultAgo)))
      case Shelf.Recent =>
        MyItems.marks.signal.map(ms => ShelfRow.recent(ms.values).map(ShelfRow.of(_, ShelfRow.defaultAgo)))
      case _ =>
        unread.signal
          .combineWith(MyItems.marks.signal)
          .map((u, _) =>
            u.flatMap(_.toOption).fold(Nil)(_.rows.map(ShelfRow.of)).filterNot(r => r.target.identity.exists(isRead))
          )
    val empty = shelf match
      case Shelf.Bookmarks =>
        "No bookmarks yet. Bookmark a source, an article, a place or a view of the map with the ribbon button."
      case Shelf.Notes =>
        "No notes yet. “Add note” on anything you read, or right-click the map to leave one at a place."
      case Shelf.Recent => "Nothing opened yet. What you open in the reading pane or in Explore is listed here."
      case _            => "Everything here is marked read."
    div(
      cls := "shelf-list",
      dataAttr("shelf") := shelf.wire.value,
      onMountCallback(_ => if shelf == Shelf.Unread then readUnread()),
      offset.signal.changes --> (_ => readUnread()),
      div(
        cls := "library-toolbar toolbar",
        span(
          cls := "library-range",
          role := "status",
          child.text <-- (shelf match
            case Shelf.Unread =>
              unread.signal.map(
                _.flatMap(_.toOption).fold("Loading…")(p =>
                  if p.total.value == 0 then "Nothing left to read"
                  else s"${p.offset.value + 1}–${p.offset.value + p.rows.size} of ${ListPage.grouped(p.total.value)} not read yet"
                )
              )
            case _ => rows.map(rs => s"${ListPage.grouped(rs.size)} ${shelf.label.value.toLowerCase}"))
        ),
        Option.when(shelf == Shelf.Unread)(
          div(
            cls := "btn-group library-pager",
            role := "group",
            aria.label := "Pages",
            button(
              typ := "button",
              cls := "btn btn--sm",
              "‹ Previous",
              disabled <-- offset.signal.map(_ <= 0),
              onClick --> (_ => offset.update(o => math.max(0, o - Layout.libraryPage.value)))
            ),
            button(
              typ := "button",
              cls := "btn btn--sm",
              "Next ›",
              disabled <-- unread.signal
                .combineWith(offset.signal)
                .map((u, o) => !u.flatMap(_.toOption).exists(p => o + p.rows.size < p.total.value)),
              onClick --> (_ => offset.update(_ + Layout.libraryPage.value))
            )
          )
        )
      ),
      child.maybe <-- unread.signal.map(
        _.flatMap(_.left.toOption).map(m => ErrorLine.text(ErrorLine.Problem(s"Could not list what is not read yet: $m")))
      ),
      ul(cls := "shelf-rows", aria.label := shelf.label.value, children <-- rows.map(_.map(row(_, shelf, section, chosen)))),
      child.maybe <-- rows.map(rs =>
        Option.when(rs.isEmpty && (shelf != Shelf.Unread || unread.now().exists(_.isRight)))(
          p(cls := "library-empty muted", empty)
        )
      )
    )

  private def row(r: ShelfRow, shelf: Shelf, section: LibrarySection, chosen: Signal[Option[ItemKey]]): HtmlElement =
    val on =
      chosen.map(c => r.target.itemKey.isDefined && c == r.target.itemKey && sectionOf(r.target.kind).contains(section))
    li(
      cls := "shelf-row",
      cls("is-chosen") <-- on,
      cls("is-unread") := r.unread,
      dataAttr("key") := r.key,
      div(
        cls := "shelf-main",
        span(cls := "shelf-kind muted", r.kind),
        a(
          cls := "shelf-title",
          href := hrefOf(r, shelf),
          Option.when(r.unread)(span(cls := "unread-dot", title := "Not read yet", aria.label := "Not read yet")),
          r.name,
          onClick --> { ev => if Router.plain(ev) then { ev.preventDefault(); open(r, shelf, section) } }
        ),
        Option.when(r.line.nonEmpty)(div(cls := "shelf-line", r.line)),
        Option.when(r.when.nonEmpty)(div(cls := "shelf-when muted", r.when))
      ),
      div(
        cls := "shelf-acts btn-group",
        r.bookmark.map(b =>
          button(
            typ := "button",
            cls := "btn btn--ghost btn--sm shelf-remove",
            "Remove",
            title := s"Remove the bookmark “${b.name}”",
            onClick --> (_ => MyItems.remove(b): Unit)
          )
        ),
        r.note.map(n =>
          button(
            typ := "button",
            cls := "btn btn--ghost btn--sm shelf-note-edit",
            "Change",
            onClick --> (_ => NoteEditor.edit(n))
          )
        ),
        r.note.map(n =>
          button(
            typ := "button",
            cls := "btn btn--ghost btn--sm shelf-note-delete",
            "Delete",
            onClick --> { _ =>
              if dom.window.confirm("Delete this note? It cannot be brought back here.") then
                MyItems.removeNote(n): Unit
            }
          )
        )
      )
    )
