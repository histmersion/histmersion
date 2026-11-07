package com.histmersion.web.marks

import com.histmersion.api.personal.{ItemKey, Target}
import com.histmersion.api.ItemKind
import com.histmersion.web.{Preview, Route}
import com.histmersion.web.library.LibraryAddress
import com.histmersion.web.LibrarySection

/** An item the person can bookmark, leave a note on or mark read, as the page knows it: what it points at and what it
  * is called. Pure.
  */
final case class ItemRef(target: Target, title: com.histmersion.api.library.Title):
  def identity: Option[Target.Identity] = target.identity
  def kind: ItemKind = target.kind

  /** Only sources and articles are marked read. */
  def readable: Boolean = ItemKind.Readable.contains(kind)

object ItemRef:
  type Href = Href.T
  object Href extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def source(ck: com.histmersion.api.library.Citekey, title: com.histmersion.api.library.Title): ItemRef = ItemRef(Target.item(ItemKind.Source, ItemKey(ck.value)), title)
  def article(slug: com.histmersion.api.research.Article.Slug, title: com.histmersion.api.library.Title): ItemRef = ItemRef(Target.item(ItemKind.Article, ItemKey(slug.value)), title)

  /** An item chosen in Explore's results or on the map. */
  def of(t: Preview.Target, title: com.histmersion.api.library.Title): ItemRef = t match
    case Preview.Target.Source(ck)    => source(ck, title)
    case Preview.Target.Article(slug) => article(slug, title)
    case Preview.Target.Place(id, _)  => ItemRef(Target.item(ItemKind.Place, ItemKey(id.value)), title)
    case Preview.Target.Event(id)     => ItemRef(Target.item(ItemKind.Event, ItemKey(id.value)), title)

  /** Where a target opens as its own page, for a link ("Open in a new tab"); a point, a view or a period has none of
    * its own but Explore.
    */
  def page(t: Target): com.histmersion.web.marks.ItemRef.Href =
    Href(t.itemKey.fold(Route.Explore.path) { key =>
      t.kind match
        case ItemKind.Source  => Route.Source(Route.Source.Citekey(key.value)).path
        case ItemKind.Article => Route.Article(Route.Article.Slug(key.value)).path
        case ItemKind.Event   => Route.Event(Route.Event.Id(key.value)).path
        case ItemKind.Place   => Route.Place(Route.Place.Id(key.value)).path
        case ItemKind.Goal    => Route.Goal(Route.Goal.Slug(key.value)).path
        case ItemKind.Picture => com.histmersion.api.media.MediaThumb.filePath(com.histmersion.api.exchange.Bundle.Asset.Uid(key.value)).value
        case _                => Route.Explore.path
    })

  /** Where a target opens back in the web UI, for a link in My library: a source or an article in Library's reading
    * pane, a goal on its page, anything else in Explore.
    */
  def back(t: Target): com.histmersion.web.marks.ItemRef.Href =
    (t.kind, t.itemKey) match
      case (ItemKind.Source, Some(k))  => Href(LibraryAddress.href(LibrarySection.Sources, Some(k)))
      case (ItemKind.Article, Some(k)) => Href(LibraryAddress.href(LibrarySection.Articles, Some(k)))
      case (ItemKind.Goal, Some(_))    => page(t)
      case (ItemKind.View, _)          => Href(Route.Explore.path + t.view.fold("")(v => s"#${v.value}"))
      case _                           => Href(Route.Explore.path)
