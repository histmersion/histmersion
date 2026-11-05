package com.histmersion.web.library

import com.histmersion.api.personal.UnreadPage
import com.histmersion.api.ItemKind
import com.histmersion.web.*
import com.histmersion.web.marks.{ItemRef, MyItems, NoteEditor, OpenBack}
import com.histmersion.web.ui.Layout
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** A shelf of My library in place of the table: the person's bookmarks, notes, what they have not marked read (the
  * tab's sources or articles, a page at a time) or what they opened lately. A row opens back where it was left — a
  * source or an article in the reading pane beside it, anything else where it lives — and a dot marks a source or an
  * article not marked read.
  */
object ShelfList:

  type IsReadResult = IsReadResult.T
  object IsReadResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsReadI = IsReadI.T
  object IsReadI extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HrefOfResult = HrefOfResult.T
  object HrefOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ElementChosen = ElementChosen.T
  object ElementChosen extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RowChosen = RowChosen.T
  object RowChosen extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def isRead(i: com.histmersion.web.library.ShelfList.IsReadI): com.histmersion.web.library.ShelfList.IsReadResult = MyItems.readNow(i)

  /** The section a source's or an article's row is read in. */
  private def sectionOf(k: ItemKind): Option[LibrarySection] = k match
    case ItemKind.Source  => Some(LibrarySection.Sources)
    case ItemKind.Article => Some(LibrarySection.Articles)
    case _                => None

  private def open(r: ShelfRow, shelf: Shelf, section: LibrarySection): Unit =
    (sectionOf(r.target.kind), r.target.itemKey) match
      case (Some(s), Some(k)) => Router.go(Route.Library(s), search = LibraryAddress.search(Some(k), shelf))
      case _                  => OpenBack(r.target)

  private def hrefOf(r: ShelfRow, shelf: Shelf): com.histmersion.web.library.ShelfList.HrefOfResult =
    (sectionOf(r.target.kind), r.target.itemKey) match
      case (Some(s), Some(k)) => LibraryAddress.href(s, Some(k), shelf)
      case _                  => ItemRef.back(r.target)

  def element(shelf: Shelf, section: LibrarySection, chosen: Signal[Option[com.histmersion.web.library.ShelfList.ElementChosen]]): HtmlElement =
    val unread = Var(Option.empty[Either[String, UnreadPage]])
    val offset = Var(0)
    def readUnread(): Unit =
      val kind = if section == LibrarySection.Articles then ItemKind.Article else ItemKind.Source
      ApiClient
        .unread(kind.wire, Layout.libraryPage, offset.now())
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
      dataAttr("shelf") := shelf.wire,
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
                  if p.total == 0 then "Nothing left to read"
                  else s"${p.offset + 1}–${p.offset + p.rows.size} of ${ListPage.grouped(p.total)} not read yet"
                )
              )
            case _ => rows.map(rs => s"${ListPage.grouped(rs.size)} ${shelf.label.toLowerCase}"))
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
              onClick --> (_ => offset.update(o => math.max(0, o - Layout.libraryPage)))
            ),
            button(
              typ := "button",
              cls := "btn btn--sm",
              "Next ›",
              disabled <-- unread.signal
                .combineWith(offset.signal)
                .map((u, o) => !u.flatMap(_.toOption).exists(p => o + p.rows.size < p.total)),
              onClick --> (_ => offset.update(_ + Layout.libraryPage))
            )
          )
        )
      ),
      child.maybe <-- unread.signal.map(
        _.flatMap(_.left.toOption).map(m => ErrorLine.text(s"Could not list what is not read yet: $m"))
      ),
      ul(cls := "shelf-rows", aria.label := shelf.label, children <-- rows.map(_.map(row(_, shelf, section, chosen)))),
      child.maybe <-- rows.map(rs =>
        Option.when(rs.isEmpty && (shelf != Shelf.Unread || unread.now().exists(_.isRight)))(
          p(cls := "library-empty muted", empty)
        )
      )
    )

  private def row(r: ShelfRow, shelf: Shelf, section: LibrarySection, chosen: Signal[Option[com.histmersion.web.library.ShelfList.RowChosen]]): HtmlElement =
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
