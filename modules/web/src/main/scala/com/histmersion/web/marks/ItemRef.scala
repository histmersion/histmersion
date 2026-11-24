package com.histmersion
package web.marks

import com.histmersion.api.personal.{ItemKey, Target}
import com.histmersion.api.ItemKind
import com.histmersion.web.{Preview, Route}
import com.histmersion.web.library.LibraryAddress
import com.histmersion.web.LibrarySection
import com.histmersion.api.library.Title
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Citekey
import com.histmersion.api.research.Article
import com.histmersion.api.media.MediaThumb
import com.histmersion.api.exchange.Bundle
import com.histmersion.lint.{tolerated, LintRule}

/** An item the person can bookmark, leave a note on or mark read, as the page knows it: what it points at and what it
  * is called. Pure.
  */
final case class ItemRef(target: Target, title: Title):
  def identity: Option[Target.Identity] = target.identity
  def kind: ItemKind = target.kind

  /** Only sources and articles are marked read. */
  def readable: Boolean = ItemKind.Readable.contains(kind)

object ItemRef:
  type Href = Href.T
  object Href extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def source(ck: Citekey, title: Title): ItemRef = ItemRef(Target.item(ItemKind.Source, ItemKey(ck.value)), title)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def article(slug: Article.Slug, title: Title): ItemRef =
    ItemRef(Target.item(ItemKind.Article, ItemKey(slug.value)), title)

  /** An item chosen in Explore's results or on the map. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(t: Preview.Target, title: Title): ItemRef = t match
    case Preview.Target.Source(ck)    => source(ck, title)
    case Preview.Target.Article(slug) => article(slug, title)
    case Preview.Target.Place(id, _)  => ItemRef(Target.item(ItemKind.Place, ItemKey(id.value)), title)
    case Preview.Target.Event(id)     => ItemRef(Target.item(ItemKind.Event, ItemKey(id.value)), title)

  /** Where a target opens as its own page, for a link ("Open in a new tab"); a point, a view or a period has none of
    * its own but Explore.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def page(t: Target): Href =
    Href(t.itemKey.fold(Route.Explore.path) { key =>
      t.kind match
        case ItemKind.Source  => Route.Source(Route.Source.Citekey(key.value)).path
        case ItemKind.Article => Route.Article(Route.Article.Slug(key.value)).path
        case ItemKind.Event   => Route.Event(Route.Event.Id(key.value)).path
        case ItemKind.Place   => Route.Place(Route.Place.Id(key.value)).path
        case ItemKind.Goal    => Route.Goal(Route.Goal.Slug(key.value)).path
        case ItemKind.Picture => MediaThumb.filePath(Bundle.Asset.Uid(key.value)).value
        case _                => Route.Explore.path
    })

  /** Where a target opens back in the web UI, for a link in My library: a source or an article in Library's reading
    * pane, a goal on its page, anything else in Explore.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def back(t: Target): Href =
    (t.kind, t.itemKey) match
      case (ItemKind.Source, Some(k))  => Href(LibraryAddress.href(LibrarySection.Sources, Some(k)))
      case (ItemKind.Article, Some(k)) => Href(LibraryAddress.href(LibrarySection.Articles, Some(k)))
      case (ItemKind.Goal, Some(_))    => page(t)
      case (ItemKind.View, _)          => Href(Route.Explore.path + t.view.fold("")(v => s"#${v.value}"))
      case _                           => Href(Route.Explore.path)
