package com.histmersion
package web.marks

import com.histmersion.api.ItemKind
import com.histmersion.web.ui.{Clipboard, ContextMenu}
import org.scalajs.dom
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** The actions on one item a row's and the reading pane's menu offer: copy it as text, as Markdown or as a link;
  * bookmark it; open it in a new tab. Each is also a button where the item is shown.
  */
object ItemMenu:
  type Href = Href.T
  object Href extends Newtype[String, Pure]
  type Markdown = Markdown.T
  object Markdown extends Newtype[String, Pure]
  type Origin = Origin.T
  object Origin extends Newtype[String, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]

  /** The item's link, whole (copied, it works elsewhere). Pure. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def link(origin: Origin, ref: ItemRef): Href = Href(origin.value.stripSuffix("/") + ItemRef.page(ref.target).value)

  /** The item as a line of plain text: its title and what the page says under it (authors, year). Pure. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def text(ref: ItemRef, byline: String): Text =
    Text(List(ref.title.value, byline).map(_.trim).filter(_.nonEmpty).mkString(" — "))

  /** The item as Markdown: a link with its title, and a source's `[[citekey]]` for the wiki. Pure. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def markdown(origin: Origin, ref: ItemRef, byline: String): Markdown =
    val title = ref.title.value.replace("[", "\\[").replace("]", "\\]")
    val cite = ref.target.itemKey.filter(_ => ref.kind == ItemKind.Source).fold("")(ck => shown" [[$ck]]")
    Markdown(
      s"[$title](${link(origin, ref).value})" + Option(byline.trim).filter(_.nonEmpty).fold("")(b => s" — $b") + cite
    )

  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def entries(ref: ItemRef, byline: String): List[ContextMenu.Entry] =
    val origin = Origin(dom.window.location.origin)
    val marked = ref.identity.exists(i => MyItems.bookmarks.now().exists(_.target.identity.contains(i)))
    List(
      ContextMenu.Entry(
        ContextMenu.Entry.Label("Copy the text"),
        () => Clipboard.copy(Clipboard.CopyText(text(ref, byline).value), Clipboard.CopyWhat("the text"))
      ),
      ContextMenu.Entry(
        ContextMenu.Entry.Label("Copy as Markdown"),
        () =>
          Clipboard.copy(Clipboard.CopyText(markdown(origin, ref, byline).value), Clipboard.CopyWhat("the Markdown"))
      ),
      ContextMenu.Entry(
        ContextMenu.Entry.Label("Copy the link"),
        () => Clipboard.copy(Clipboard.CopyText(link(origin, ref).value), Clipboard.CopyWhat("the link"))
      ),
      ContextMenu.Separator,
      ContextMenu.Entry(
        if marked then ContextMenu.Entry.Label("Remove the bookmark") else ContextMenu.Entry.Label("Bookmark"),
        () => ItemActions.bookmark(ref)
      ),
      ContextMenu.Entry(
        ContextMenu.Entry.Label("Open in a new tab"),
        () => { val _ = dom.window.open(ItemRef.page(ref.target).value, "_blank", "noopener") }
      )
    )
