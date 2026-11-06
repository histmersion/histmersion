package com.histmersion.web.marks

import com.histmersion.api.personal.Target
import com.histmersion.api.ItemKind
import com.histmersion.web.{Preview, Route}
import com.histmersion.web.library.LibraryAddress
import com.histmersion.web.LibrarySection

/** An item the person can bookmark, leave a note on or mark read, as the page knows it: what it points at and what it
  * is called. Pure.
  */
final case class ItemRef(target: Target, title: ItemRef.Title):
  def identity: Option[com.histmersion.web.marks.ItemRef.IdentityResult] = target.identity
  def kind: ItemKind = target.kind

  /** Only sources and articles are marked read. */
  def readable: com.histmersion.web.marks.ItemRef.ReadableResult = ItemKind.Readable.contains(kind)

object ItemRef:

  type IdentityResult = IdentityResult.T
  object IdentityResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadableResult = ReadableResult.T
  object ReadableResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceCk = SourceCk.T
  object SourceCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceTitle = _root_.com.histmersion.web.marks.ItemRef.Title
  val SourceTitle: _root_.com.histmersion.web.marks.ItemRef.Title.type = _root_.com.histmersion.web.marks.ItemRef.Title
  type ArticleSlug = ArticleSlug.T
  object ArticleSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleTitle = _root_.com.histmersion.web.marks.ItemRef.Title
  val ArticleTitle: _root_.com.histmersion.web.marks.ItemRef.Title.type = _root_.com.histmersion.web.marks.ItemRef.Title
  type OfTitle = _root_.com.histmersion.web.marks.ItemRef.Title
  val OfTitle: _root_.com.histmersion.web.marks.ItemRef.Title.type = _root_.com.histmersion.web.marks.ItemRef.Title
  type PageResult = PageResult.T
  object PageResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BackResult = BackResult.T
  object BackResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def source(ck: com.histmersion.web.marks.ItemRef.SourceCk, title: com.histmersion.web.marks.ItemRef.SourceTitle): ItemRef = ItemRef(Target.item(ItemKind.Source, ck), title)
  def article(slug: com.histmersion.web.marks.ItemRef.ArticleSlug, title: com.histmersion.web.marks.ItemRef.ArticleTitle): ItemRef = ItemRef(Target.item(ItemKind.Article, slug), title)

  /** An item chosen in Explore's results or on the map. */
  def of(t: Preview.Target, title: com.histmersion.web.marks.ItemRef.OfTitle): ItemRef = t match
    case Preview.Target.Source(ck)    => source(ck, title)
    case Preview.Target.Article(slug) => article(slug, title)
    case Preview.Target.Place(id, _)  => ItemRef(Target.item(ItemKind.Place, id), title)
    case Preview.Target.Event(id)     => ItemRef(Target.item(ItemKind.Event, id), title)

  /** Where a target opens as its own page, for a link ("Open in a new tab"); a point, a view or a period has none of
    * its own but Explore.
    */
  def page(t: Target): com.histmersion.web.marks.ItemRef.PageResult =
    t.itemKey.fold(Route.Explore.path) { key =>
      t.kind match
        case ItemKind.Source  => Route.Source(key).path
        case ItemKind.Article => Route.Article(key).path
        case ItemKind.Event   => Route.Event(key).path
        case ItemKind.Place   => Route.Place(key).path
        case ItemKind.Goal    => Route.Goal(key).path
        case ItemKind.Picture => com.histmersion.api.media.MediaThumb.filePath(key)
        case _                => Route.Explore.path
    }

  /** Where a target opens back in the web UI, for a link in My library: a source or an article in Library's reading
    * pane, a goal on its page, anything else in Explore.
    */
  def back(t: Target): com.histmersion.web.marks.ItemRef.BackResult =
    (t.kind, t.itemKey) match
      case (ItemKind.Source, Some(k))  => LibraryAddress.href(LibrarySection.Sources, Some(k))
      case (ItemKind.Article, Some(k)) => LibraryAddress.href(LibrarySection.Articles, Some(k))
      case (ItemKind.Goal, Some(_))    => page(t)
      case (ItemKind.View, _)          => Route.Explore.path + t.view.fold("")(v => s"#${v.value}")
      case _                           => Route.Explore.path
