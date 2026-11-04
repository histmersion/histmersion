package com.histmersion.cli

import com.monovore.decline.Command
import com.histmersion.config.*
import com.histmersion.api.checks.{FormatField, FormatIssue, FormatMarkdown, FormatReport, FormatRule, FormatSeverity}

/** The formatting-lint commands parse (`article lint <slug> [--fix]`, `lint --source/--article/--file`, `--strict` on
  * the writes, `places rename`), and `lint --file` checks a note's BODY: its own frontmatter is the metadata
  * `article put` sends, not pasted text.
  */
class FormatCmdSuite extends munit.FunSuite:

  private given console: com.histmersion.util.console.Console = com.histmersion.util.console.Console.standard

  private def parses(cmd: com.monovore.decline.Opts[?], args: String*): Boolean =
    Command("histmersion", "test")(cmd).parse(args.toList, Map.empty).isRight

  test("article lint <slug> [--fix] [--json]; patch and put take --strict"):
    assert(parses(ArticleCli.command, "article", "lint", "sea_peoples"))
    assert(parses(ArticleCli.command, "article", "lint", "sea_peoples", "--fix", "--json"))
    assert(!parses(ArticleCli.command, "article", "lint"))
    assert(parses(ArticleCli.command, "article", "patch", "sea_peoples", "--title", "T", "--strict"))
    assert(parses(ArticleCli.command, "article", "put", "x.md", "--strict"))

  test("lint takes --article, --source or --file, with --fix"):
    assert(parses(Ops.lint, "lint", "--source", "haak2015_massive", "--fix"))
    assert(parses(Ops.lint, "lint", "--article", "sea_peoples"))
    assert(parses(Ops.lint, "lint", "--file", "draft.md", "--fix", "--json"))
    assert(parses(Ops.lint, "lint", "--rule", "article-markdown", "--rule", "note-markdown"))

  test("places rename <old> <new>; an unusable id is refused before anything is sent"):
    assert(parses(PlacesCmd.command, "places", "rename", "zz_old", "zz_new"))
    assert(!parses(PlacesCmd.command, "places", "rename", "zz_old", "Not An Id"))
    assert(!parses(PlacesCmd.command, "places", "rename", "zz_old", "zz_old"))

  test("a note file's own frontmatter is split off; a body is all body"):
    val note = "---\ntype: concept\nslug: x\n---\n\n# X\n\nText.\n"
    assertEquals(FileFormat.splitFrontmatter(note), ("---\ntype: concept\nslug: x\n---\n", "\n# X\n\nText.\n"))
    val (h, b) = FileFormat.splitFrontmatter(note)
    assertEquals(h + b, note)
    assertEquals(FileFormat.splitFrontmatter("# X\n\nText.\n"), ("", "# X\n\nText.\n"))
    // an opening `---` with no closing one is not a frontmatter block
    assertEquals(FileFormat.splitFrontmatter("---\nnot closed\n"), ("", "---\nnot closed\n"))

  test("the Markdown rendering of a report: counts, one line per problem, the fix"):
    val i = FormatIssue(
      FormatRule.TableNotRendered,
      FormatSeverity.Error,
      FormatField.Body,
      4,
      1,
      "| a | b |",
      "no blank line",
      Some("insert a blank line before the table")
    )
    val md = FormatMarkdown.report(FormatReport("sea_peoples", List(i)))
    assert(md.startsWith("### sea_peoples: 1 formatting problem (1 error(s), 0 warning(s))"), md)
    assert(
      md.contains("- body L4:1 error table-not-rendered — no blank line · `| a | b |` · fix: insert a blank line"),
      md
    )
    assertEquals(FormatMarkdown.report(FormatReport("x", Nil)), "### x: no formatting problems")
    assertEquals(FormatMarkdown.notice(Nil), "")
    assert(
      FormatMarkdown.notice(List(i)).contains("## Formatting: 1 formatting problem (1 will not display as intended)")
    )
