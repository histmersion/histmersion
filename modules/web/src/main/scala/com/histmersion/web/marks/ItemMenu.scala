package com.histmersion.web.marks

import com.histmersion.api.ItemKind
import com.histmersion.web.ui.{Clipboard, ContextMenu}
import org.scalajs.dom

/** The actions on one item a row's and the reading pane's menu offer: copy it as text, as Markdown or as a link;
  * bookmark it; open it in a new tab. Each is also a button where the item is shown.
  */
object ItemMenu:
  type Href = Href.T
  object Href extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Markdown = Markdown.T
  object Markdown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Origin = Origin.T
  object Origin extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The item's link, whole (copied, it works elsewhere). Pure. */
  def link(origin: com.histmersion.web.marks.ItemMenu.Origin, ref: ItemRef): com.histmersion.web.marks.ItemMenu.Href = Href(origin.value.stripSuffix("/") + ItemRef.page(ref.target).value)

  /** The item as a line of plain text: its title and what the page says under it (authors, year). Pure. */
  def text(ref: ItemRef, byline: String): com.histmersion.web.marks.ItemMenu.Text =
    Text(List(ref.title.value, byline).map(_.trim).filter(_.nonEmpty).mkString(" — "))

  /** The item as Markdown: a link with its title, and a source's `[[citekey]]` for the wiki. Pure. */
  def markdown(origin: com.histmersion.web.marks.ItemMenu.Origin, ref: ItemRef, byline: String): com.histmersion.web.marks.ItemMenu.Markdown =
    val title = ref.title.value.replace("[", "\\[").replace("]", "\\]")
    val cite = ref.target.itemKey.filter(_ => ref.kind == ItemKind.Source).fold("")(ck => s" [[${ck.value}]]")
    Markdown(s"[$title](${link(origin, ref).value})" + Option(byline.trim).filter(_.nonEmpty).fold("")(b => s" — $b") + cite)

  def entries(ref: ItemRef, byline: String): List[ContextMenu.Entry] =
    val origin = Origin(dom.window.location.origin)
    val marked = ref.identity.exists(i => MyItems.bookmarks.now().exists(_.target.identity.contains(i)))
    List(
      ContextMenu.Entry(com.histmersion.web.ui.ContextMenu.Entry.Label("Copy the text"), () => Clipboard.copy(Clipboard.CopyText(text(ref, byline).value), com.histmersion.web.ui.Clipboard.CopyWhat("the text"))),
      ContextMenu.Entry(com.histmersion.web.ui.ContextMenu.Entry.Label("Copy as Markdown"), () => Clipboard.copy(Clipboard.CopyText(markdown(origin, ref, byline).value), com.histmersion.web.ui.Clipboard.CopyWhat("the Markdown"))),
      ContextMenu.Entry(com.histmersion.web.ui.ContextMenu.Entry.Label("Copy the link"), () => Clipboard.copy(Clipboard.CopyText(link(origin, ref).value), com.histmersion.web.ui.Clipboard.CopyWhat("the link"))),
      ContextMenu.Separator,
      ContextMenu.Entry(if marked then com.histmersion.web.ui.ContextMenu.Entry.Label("Remove the bookmark") else com.histmersion.web.ui.ContextMenu.Entry.Label("Bookmark"), () => ItemActions.bookmark(ref)),
      ContextMenu.Entry(
        com.histmersion.web.ui.ContextMenu.Entry.Label("Open in a new tab"),
        () => { val _ = dom.window.open(ItemRef.page(ref.target).value, "_blank", "noopener") }
      )
    )
