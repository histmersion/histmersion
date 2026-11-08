package com.histmersion
package web.marks

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.web.marks.NoteMarkdown.{Block, Inline}
import com.histmersion.api.library.Citekey

class NoteMarkdownSuite extends munit.FunSuite:

  test("blocks: a heading, a paragraph over two lines, a list, a quote, code"):
    val text = NoteMarkdown.Markdown("# Bridge\nIt stands\nnear **Tiryns**.\n\n- one\n- two\n\n> a quote\n\n```\nx = 1\n```\n1. first")
    assertEquals(
      NoteMarkdown.blocks(text),
      List(
        Block.Heading(Block.Heading.Level(1), List(Inline.Text("Bridge"))),
        Block.Para(List(Inline.Text("It stands near "), Inline.Strong("Tiryns"), Inline.Text("."))),
        Block.Items(Block.Items.Ordered(false), List(List(Inline.Text("one")), List(Inline.Text("two")))),
        Block.Quote(List(Inline.Text("a quote"))),
        Block.CodeBlock("x = 1"),
        Block.Items(Block.Items.Ordered(true), List(List(Inline.Text("first"))))
      )
    )

  test("inlines: emphasis, code, a citekey, links — only links a person can follow safely"):
    assertEquals(
      NoteMarkdown.inlines(NoteMarkdown.Markdown("*a* _b_ `c` [[cline2014_year]] [w](https://example.org) [x](javascript:alert(1))")),
      List(
        Inline.Em("a"),
        Inline.Text(" "),
        Inline.Em("b"),
        Inline.Text(" "),
        Inline.Code("c"),
        Inline.Text(" "),
        Inline.Cite(Citekey("cline2014_year")),
        Inline.Text(" "),
        Inline.Link("w", NoteMarkdown.Href("https://example.org")),
        Inline.Text(" "),
        Inline.Text("[x](javascript:alert(1)"),
        Inline.Text(")")
      )
    )
    assertEquals(NoteMarkdown.safeHref(NoteMarkdown.Href("/library?item=x")), Some("/library?item=x"))
    assertEquals(NoteMarkdown.safeHref(NoteMarkdown.Href("//evil.example.org")), None)

  test("nothing written is nothing shown"):
    assertEquals(NoteMarkdown.blocks(NoteMarkdown.Markdown("\n\n  \n")), Nil)
