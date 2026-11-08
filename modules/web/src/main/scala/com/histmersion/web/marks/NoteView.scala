package com.histmersion
package web.marks

import com.histmersion.web.SourceLink
import com.raquo.laminar.api.L.*

/** A note's text as the page shows it ([[NoteMarkdown]], built as elements: nothing the person wrote is read as HTML).
  */
object NoteView:


  def apply(text: NoteMarkdown.Markdown): HtmlElement =
    div(cls := "note-text reading", NoteMarkdown.blocks(text).map(block))

  private def block(b: NoteMarkdown.Block): HtmlElement = b match
    case NoteMarkdown.Block.Heading(n, t) => htmlTag(s"h${math.min(6, n.value + 2)}")(cls := "note-heading", t.map(inline))
    case NoteMarkdown.Block.Para(t)       => p(t.map(inline))
    case NoteMarkdown.Block.Items(ordered, is) => (if ordered.value then ol() else ul()).amend(is.map(i => li(i.map(inline))))
    case NoteMarkdown.Block.Quote(t)           => blockQuote(t.map(inline))
    case NoteMarkdown.Block.CodeBlock(c)       => pre(code(c))

  private def inline(i: NoteMarkdown.Inline): Node = i match
    case NoteMarkdown.Inline.Text(s)     => textToTextNode(s)
    case NoteMarkdown.Inline.Strong(s)   => strong(s)
    case NoteMarkdown.Inline.Em(s)       => em(s)
    case NoteMarkdown.Inline.Code(s)     => code(s)
    case NoteMarkdown.Inline.Link(t, to) =>
      if to.value.startsWith("/") then a(href := to.value, t) else a(href := to.value, target := "_blank", rel := "noopener", t)
    case NoteMarkdown.Inline.Cite(ck) => a(href := SourceLink.href(ck), cls := "note-cite", ck.value)
