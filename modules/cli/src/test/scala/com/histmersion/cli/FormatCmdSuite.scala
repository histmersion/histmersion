package com.histmersion
package cli

import com.histmersion.config.*
import com.histmersion.api.checks.{FormatField, FormatIssue, FormatMarkdown, FormatReport, FormatRule, FormatSeverity}

/** The formatting-lint commands parse (`article lint <slug> [--fix]`, `lint --source/--article/--file`, `--strict` on
  * the writes, `places rename`), and `lint --file` checks a note's BODY: its own frontmatter is the metadata
  * `article put` sends, not pasted text.
  */
class FormatCmdSuite extends munit.FunSuite:

  private given console: util.console.Console = util.console.Console.standard

  test("article lint <slug> [--fix] [--json]; patch and put take --strict"):
    assert(Parsing.parses("article", "lint", "sea_peoples"))
    assert(Parsing.parses("article", "lint", "sea_peoples", "--fix", "--json"))
    assert(!Parsing.parses("article", "lint"))
    assert(Parsing.parses("article", "patch", "sea_peoples", "--title", "T", "--strict"))
    assert(Parsing.parses("article", "put", "x.md", "--strict"))

  test("lint takes --article, --source or --file, with --fix"):
    assert(Parsing.parses("lint", "--source", "haak2015_massive", "--fix"))
    assert(Parsing.parses("lint", "--article", "sea_peoples"))
    assert(Parsing.parses("lint", "--file", "draft.md", "--fix", "--json"))
    assert(Parsing.parses("lint", "--rule", "article-markdown", "--rule", "note-markdown"))

  test("places rename <old> <new>; an unusable id is refused before anything is sent"):
    assert(Parsing.parses("places", "rename", "zz_old", "zz_new"))
    assert(!Parsing.parses("places", "rename", "zz_old", "Not An Id"))
    assert(!Parsing.parses("places", "rename", "zz_old", "zz_old"))

  test("a note file's own frontmatter is split off; a body is all body"):
    val note = FileFormat.Text("---\ntype: concept\nslug: x\n---\n\n# X\n\nText.\n")
    assertEquals(
      FileFormat.splitFrontmatter(note),
      (FileFormat.Frontmatter("---\ntype: concept\nslug: x\n---\n"), FileFormat.Body("\n# X\n\nText.\n"))
    )
    val (h, b) = FileFormat.splitFrontmatter(note)
    assertEquals(h.value + b.value, note.value)
    assertEquals(
      FileFormat.splitFrontmatter(FileFormat.Text("# X\n\nText.\n")),
      (FileFormat.Frontmatter(""), FileFormat.Body("# X\n\nText.\n"))
    )
    // an opening `---` with no closing one is not a frontmatter block
    assertEquals(
      FileFormat.splitFrontmatter(FileFormat.Text("---\nnot closed\n")),
      (FileFormat.Frontmatter(""), FileFormat.Body("---\nnot closed\n"))
    )

  test("the Markdown rendering of a report: counts, one line per problem, the fix"):
    val i = FormatIssue(
      FormatRule.TableNotRendered,
      FormatSeverity.Error,
      FormatField.Body,
      FormatIssue.Line(4),
      FormatIssue.Column(1),
      FormatIssue.Excerpt("| a | b |"),
      FormatIssue.Message("no blank line"),
      Some(FormatIssue.Fix("insert a blank line before the table"))
    )
    val md = FormatMarkdown.report(FormatReport(FormatReport.Subject("sea_peoples"), List(i)))
    assert(md.startsWith("### sea_peoples: 1 formatting problem (1 error(s), 0 warning(s))"), md)
    assert(
      md.contains("- body L4:1 error table-not-rendered — no blank line · `| a | b |` · fix: insert a blank line"),
      md
    )
    assertEquals(FormatMarkdown.report(FormatReport(FormatReport.Subject("x"), Nil)), "### x: no formatting problems")
    assertEquals(FormatMarkdown.notice(Nil), "")
    assert(
      FormatMarkdown.notice(List(i)).contains("## Formatting: 1 formatting problem (1 will not display as intended)")
    )
