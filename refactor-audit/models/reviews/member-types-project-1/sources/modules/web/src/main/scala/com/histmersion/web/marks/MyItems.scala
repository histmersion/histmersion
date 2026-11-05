package com.histmersion.web.marks

import com.histmersion.api.personal.*
import com.histmersion.web.{ApiClient, ApiFailure, Auth}
import com.raquo.laminar.api.L.*

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** The person's bookmarks, notes and read marks, as the page holds them: read once when it opens, changed in place by
  * each write the server accepted, so every row, header and list shows the same state at once. A change refused says
  * why ([[problem]]).
  */
object MyItems:

  type MarksPart1 = MarksPart1.T
  object MarksPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Loaded = Loaded.T
  object Loaded extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkKeyResult = MarkKeyResult.T
  object MarkKeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BookmarkOfIdentity = BookmarkOfIdentity.T
  object BookmarkOfIdentity extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BookmarkedResult = BookmarkedResult.T
  object BookmarkedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AddLabel = AddLabel.T
  object AddLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AddTitle = AddTitle.T
  object AddTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameLabel = RenameLabel.T
  object RenameLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotesOnIdentity = NotesOnIdentity.T
  object NotesOnIdentity extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AddNoteText = AddNoteText.T
  object AddNoteText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AddNoteTitle = AddNoteTitle.T
  object AddNoteTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EditNoteText = EditNoteText.T
  object EditNoteText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsReadResult = IsReadResult.T
  object IsReadResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadNowResult = ReadNowResult.T
  object ReadNowResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadNowIdentity = ReadNowIdentity.T
  object ReadNowIdentity extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetReadRead = SetReadRead.T
  object SetReadRead extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val bookmarks: Var[List[Bookmark]] = Var(Nil)
  val notes: Var[List[Note]] = Var(Nil)

  /** By [[Target.identity]]. */
  val marks: Var[Map[com.histmersion.web.marks.MyItems.MarksPart1, ReadMark]] = Var(Map.empty)

  /** The last change that did not go through, and why. */
  val problem: Var[Option[com.histmersion.web.marks.MyItems.Problem]] = Var(None)
  val loaded: Var[com.histmersion.web.marks.MyItems.Loaded] = Var(false)

  private def markKey(m: ReadMark): com.histmersion.web.marks.MyItems.MarkKeyResult = s"${m.kind.wire}:${m.id.value}"

  def load(): Unit =
    ApiClient.bookmarks().zip(ApiClient.notes()).zip(ApiClient.readMarks()).foreach { case ((bs, ns), ms) =>
      bookmarks.set(bs)
      notes.set(ns)
      marks.set(ms.map(m => markKey(m) -> m).toMap)
      loaded.set(true)
    }

  private def refused(e: Throwable): Unit =
    problem.set(Some(e match
      case f: ApiFailure => f.getMessage
      case other         => Option(other.getMessage).getOrElse("the server could not be reached")))

  private def tracked[A](f: Future[A])(ok: A => Unit): Future[A] =
    problem.set(None)
    f.onComplete(_.fold(refused, ok))
    f

  // ── bookmarks ───────────────────────────────────────────────────────────────────────────────────

  def bookmarkOf(identity: com.histmersion.web.marks.MyItems.BookmarkOfIdentity): Signal[Option[Bookmark]] =
    bookmarks.signal.map(_.find(_.target.identity.contains(identity)))

  def bookmarked(ref: ItemRef): Signal[com.histmersion.web.marks.MyItems.BookmarkedResult] =
    ref.identity.fold(Val(false): Signal[Boolean])(i => bookmarkOf(i).map(_.isDefined))

  def add(target: Target, label: Option[com.histmersion.web.marks.MyItems.AddLabel], title: com.histmersion.web.marks.MyItems.AddTitle): Future[Bookmark] =
    tracked(ApiClient.addBookmark(Bookmark.New(target, label.map(Bookmark.Label(_)), Some(title)))) { b =>
      bookmarks.update(bs => b :: bs.filterNot(_.id == b.id))
    }

  def remove(b: Bookmark): Future[Bookmark] =
    tracked(ApiClient.removeBookmark(b.id.value))(_ => bookmarks.update(_.filterNot(_.id == b.id)))

  def rename(b: Bookmark, label: Option[com.histmersion.web.marks.MyItems.RenameLabel]): Future[Bookmark] =
    tracked(ApiClient.editBookmark(b.id.value, Bookmark.Edit(label.map(Bookmark.Label(_)))))(n =>
      bookmarks.update(_.map(x => if x.id == n.id then n else x))
    )

  /** Bookmark the item, or take its bookmark away. */
  def toggle(ref: ItemRef): Unit =
    ref.identity.flatMap(i => bookmarks.now().find(_.target.identity.contains(i))) match
      case Some(b) => remove(b): Unit
      case None    => add(ref.target, None, ref.title): Unit

  // ── notes ───────────────────────────────────────────────────────────────────────────────────────

  def notesOn(identity: com.histmersion.web.marks.MyItems.NotesOnIdentity): Signal[List[Note]] = notes.signal.map(_.filter(_.target.identity.contains(identity)))

  def addNote(target: Target, text: com.histmersion.web.marks.MyItems.AddNoteText, title: com.histmersion.web.marks.MyItems.AddNoteTitle): Future[Note] =
    tracked(ApiClient.addNote(Note.New(target, Note.Text(text), Some(title))))(n => notes.update(n :: _))

  def editNote(n: Note, text: com.histmersion.web.marks.MyItems.EditNoteText): Future[Note] =
    tracked(ApiClient.editNote(n.id.value, Note.Edit(Note.Text(text), Some(n.updatedAt))))(m =>
      notes.update(ns => m :: ns.filterNot(_.id == m.id))
    )

  def removeNote(n: Note): Future[Note] =
    tracked(ApiClient.removeNote(n.id.value))(_ => notes.update(_.filterNot(_.id == n.id)))

  // ── read marks ──────────────────────────────────────────────────────────────────────────────────

  def isRead(ref: ItemRef): Signal[com.histmersion.web.marks.MyItems.IsReadResult] =
    ref.identity.fold(Val(false): Signal[Boolean])(i => marks.signal.map(_.get(i).exists(_.read)))

  def readNow(identity: com.histmersion.web.marks.MyItems.ReadNowIdentity): com.histmersion.web.marks.MyItems.ReadNowResult = marks.now().get(identity).exists(_.read)

  def setRead(ref: ItemRef, read: com.histmersion.web.marks.MyItems.SetReadRead): Unit =
    ref.target.itemKey.foreach { key =>
      tracked(ApiClient.setRead(ReadMark.Set(ref.kind, ItemKey(key), read, Some(ref.title))))(m =>
        marks.update(_ + (markKey(m) -> m))
      ): Unit
    }

  /** The person opened `ref` (Library's reading pane, an item's detail): kept for "Recently opened" when the page may
    * write; nothing is marked read.
    */
  def opened(ref: ItemRef): Unit =
    if ref.readable && Auth.writableNow then
      ref.target.itemKey.foreach { key =>
        ApiClient
          .opened(ReadOpened(ref.kind, ItemKey(key), Some(ref.title)))
          .foreach(m => marks.update(_ + (markKey(m) -> m)))
      }
