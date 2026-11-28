package com.histmersion
package web.marks
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.api.personal.*
import com.histmersion.web.{ApiClient, ApiFailure, Auth}
import com.raquo.laminar.api.L.*

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Title
import com.histmersion.lint.{tolerated, LintRule}

/** The person's bookmarks, notes and read marks, as the page holds them: read once when it opens, changed in place by
  * each write the server accepted, so every row, header and list shows the same state at once. A change refused says
  * why ([[problem]]).
  */
object MyItems:
  enum Loaded derives CanEqual:
    case Loaded, Pending
  object Loaded:
    extension (loaded: Loaded) def loaded: Boolean = loaded == Loaded

  val bookmarks: Var[List[Bookmark]] = Var(Nil)
  val notes: Var[List[Note]] = Var(Nil)

  /** By [[Target.identity]]. */
  val marks: Var[Map[Target.Identity, ReadMark]] = Var(Map.empty)

  /** The last change that did not go through, and why. */
  val problem: Var[Option[Problem]] = Var(None)
  val loaded: Var[Loaded] = Var(Loaded.Pending)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def markKey(m: ReadMark): Target.Identity = Target.Identity(s"${m.kind.wire}:${m.id.value}")

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  def load(): Unit =
    ApiClient.bookmarks().zip(ApiClient.notes()).zip(ApiClient.readMarks()).foreach { case ((bs, ns), ms) =>
      bookmarks.set(bs)
      notes.set(ns)
      marks.set(ms.map(m => markKey(m) -> m).toMap)
      loaded.set(Loaded.Loaded)
    }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  private def refused(e: Throwable): Unit =
    problem.set(Some(e match
      case f: ApiFailure => Problem.Unclassified(Legacy.Text(f.getMessage))
      case other         =>
        Problem.Unclassified(Legacy.Text(Option(other.getMessage).getOrElse("the server could not be reached")))))

  private def tracked[A](f: Future[A])(ok: A => Unit): Future[A] =
    problem.set(None)
    f.onComplete(_.fold(refused, ok))
    f

  // ── bookmarks ───────────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def bookmarkOf(identity: Target.Identity): Signal[Option[Bookmark]] =
    bookmarks.signal.map(_.find(_.target.identity.contains(identity)))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def bookmarked(ref: ItemRef): Signal[Boolean] =
    ref.identity.fold(Val(false): Signal[Boolean])(i => bookmarkOf(i).map(_.isDefined))

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def add(target: Target, label: Option[Bookmark.Label], title: Title): Future[Bookmark] =
    tracked(ApiClient.addBookmark(Bookmark.New(target, label, Some(title)))) { b =>
      bookmarks.update(bs => b :: bs.filterNot(_.id == b.id))
    }

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def remove(b: Bookmark): Future[Bookmark] =
    tracked(ApiClient.removeBookmark(b.id))(_ => bookmarks.update(_.filterNot(_.id == b.id)))

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def rename(b: Bookmark, label: Option[Bookmark.Label]): Future[Bookmark] =
    tracked(ApiClient.editBookmark(b.id, Bookmark.Edit(label)))(n =>
      bookmarks.update(_.map(x => if x.id == n.id then n else x))
    )

  /** Bookmark the item, or take its bookmark away. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def toggle(ref: ItemRef): Unit =
    ref.identity.flatMap(i => bookmarks.now().find(_.target.identity.contains(i))) match
      case Some(b) => remove(b): Unit
      case None    => add(ref.target, None, ref.title): Unit

  // ── notes ───────────────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def notesOn(identity: Target.Identity): Signal[List[Note]] =
    notes.signal.map(_.filter(_.target.identity.contains(identity)))

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def addNote(target: Target, text: Note.Text, title: Title): Future[Note] =
    tracked(ApiClient.addNote(Note.New(target, text, Some(title))))(n => notes.update(n :: _))

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def editNote(n: Note, text: Note.Text): Future[Note] =
    tracked(ApiClient.editNote(n.id, Note.Edit(text, Some(n.updatedAt))))(m =>
      notes.update(ns => m :: ns.filterNot(_.id == m.id))
    )

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def removeNote(n: Note): Future[Note] =
    tracked(ApiClient.removeNote(n.id))(_ => notes.update(_.filterNot(_.id == n.id)))

  // ── read marks ──────────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def isRead(ref: ItemRef): Signal[Boolean] =
    ref.identity.fold(Val(false): Signal[Boolean])(i => marks.signal.map(_.get(i).exists(_.read.read)))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def readNow(identity: Target.Identity): Boolean = marks.now().get(identity).exists(_.read.read)

  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def setRead(ref: ItemRef, read: ReadMark.Read): Unit =
    ref.target.itemKey.foreach { key =>
      tracked(ApiClient.setRead(ReadMark.Set(ref.kind, key, read, Some(ref.title))))(m =>
        marks.update(_ + (markKey(m) -> m))
      ): Unit
    }

  /** The person opened `ref` (Library's reading pane, an item's detail): kept for "Recently opened" when the page may
    * write; nothing is marked read.
    */
  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def opened(ref: ItemRef): Unit =
    if ref.readable && Auth.writableNow then
      ref.target.itemKey.foreach { key =>
        ApiClient
          .opened(ReadOpened(ref.kind, key, Some(ref.title)))
          .foreach(m => marks.update(_ + (markKey(m) -> m)))
      }
