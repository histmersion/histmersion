package com.histmersion.web.marks

import com.histmersion.api.ItemKind
import com.histmersion.web.ui.{Clipboard, ContextMenu}
import org.scalajs.dom

/** The actions on one item a row's and the reading pane's menu offer: copy it as text, as Markdown or as a link;
  * bookmark it; open it in a new tab. Each is also a button where the item is shown.
  */
object ItemMenu:

  /** The item's link, whole (copied, it works elsewhere). Pure. */
  type LinkResult = LinkResult.T
  object LinkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkOrigin = LinkOrigin.T
  object LinkOrigin extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextResult = TextResult.T
  object TextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextByline = TextByline.T
  object TextByline extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkdownResult = MarkdownResult.T
  object MarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkdownOrigin = MarkdownOrigin.T
  object MarkdownOrigin extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkdownByline = MarkdownByline.T
  object MarkdownByline extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EntriesByline = EntriesByline.T
  object EntriesByline extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def link(origin: com.histmersion.web.marks.ItemMenu.LinkOrigin, ref: ItemRef): com.histmersion.web.marks.ItemMenu.LinkResult = origin.stripSuffix("/") + ItemRef.page(ref.target)

  /** The item as a line of plain text: its title and what the page says under it (authors, year). Pure. */
  def text(ref: ItemRef, byline: com.histmersion.web.marks.ItemMenu.TextByline): com.histmersion.web.marks.ItemMenu.TextResult =
    List(ref.title, byline).map(_.trim).filter(_.nonEmpty).mkString(" — ")

  /** The item as Markdown: a link with its title, and a source's `[[citekey]]` for the wiki. Pure. */
  def markdown(origin: com.histmersion.web.marks.ItemMenu.MarkdownOrigin, ref: ItemRef, byline: com.histmersion.web.marks.ItemMenu.MarkdownByline): com.histmersion.web.marks.ItemMenu.MarkdownResult =
    val title = ref.title.replace("[", "\\[").replace("]", "\\]")
    val cite = ref.target.itemKey.filter(_ => ref.kind == ItemKind.Source).fold("")(ck => s" [[$ck]]")
    s"[$title](${link(origin, ref)})" + Option(byline.trim).filter(_.nonEmpty).fold("")(b => s" — $b") + cite

  def entries(ref: ItemRef, byline: com.histmersion.web.marks.ItemMenu.EntriesByline): List[ContextMenu.Entry] =
    val origin = dom.window.location.origin
    val marked = ref.identity.exists(i => MyItems.bookmarks.now().exists(_.target.identity.contains(i)))
    List(
      ContextMenu.Entry("Copy the text", () => Clipboard.copy(text(ref, byline), "the text")),
      ContextMenu.Entry("Copy as Markdown", () => Clipboard.copy(markdown(origin, ref, byline), "the Markdown")),
      ContextMenu.Entry("Copy the link", () => Clipboard.copy(link(origin, ref), "the link")),
      ContextMenu.Separator,
      ContextMenu.Entry(if marked then "Remove the bookmark" else "Bookmark", () => ItemActions.bookmark(ref)),
      ContextMenu.Entry(
        "Open in a new tab",
        () => { val _ = dom.window.open(ItemRef.page(ref.target), "_blank", "noopener") }
      )
    )
