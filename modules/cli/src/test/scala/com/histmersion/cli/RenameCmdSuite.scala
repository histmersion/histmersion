package com.histmersion
package cli

import com.histmersion.api.library.Source
import com.histmersion.api.library.Citekey
import com.histmersion.lint.{tolerated, LintRule}

/** `histmersion rename <old> <new> [--json]`: the arguments parse, and a new citekey the server would refuse is a usage
  * error here, before any request.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class RenameCmdSuite extends munit.FunSuite:

  private given console: util.console.Console = util.console.Console.standard

  private def parse(args: String*) = Parsing.parse("rename" +: args*)

  test("two positional citekeys, --json optional"):
    assertEquals(
      parse("page2024_history", "page1959_history"),
      Right(RenameCmd.Args(Citekey("page2024_history"), Citekey("page1959_history"), json = Cli.JsonOutput.Text))
    )
    assertEquals(
      parse("page2024_history", "page1959_history", "--json"),
      Right(RenameCmd.Args(Citekey("page2024_history"), Citekey("page1959_history"), json = Cli.JsonOutput.Json))
    )

  test("the new citekey is trimmed"):
    assertEquals(
      parse("latacz2023_troy", " latacz2004_troy "),
      Right(RenameCmd.Args(Citekey("latacz2023_troy"), Citekey("latacz2004_troy"), json = Cli.JsonOutput.Text))
    )

  test("a missing argument is a usage error"):
    assert(parse("page2024_history").isLeft)
    assert(parse().isLeft)

  test("citekey validity is decided by the server") {
    List("page2024_history", "Page 1959", "page-1959").foreach(to =>
      assertEquals(
        parse("page2024_history", to),
        Right(RenameCmd.Args(Citekey("page2024_history"), Citekey(to), json = Cli.JsonOutput.Text))
      )
    )
  }

  test("the subcommand is registered under `rename`"):
    assert(Parsing.parses("rename", "a2020_x", "a1999_x"))
    assert(Parsing.help("rename", "--help").contains("alias"))

  test("a full export removes the mirror notes of renamed citekeys, and nothing else"):
    import java.nio.file.Files
    val dir = Files.createTempDirectory("rename-prune")
    try
      List("old2020_a.md", "new1999_a.md", "keep2020_b.md").foreach(n => Files.writeString(dir.resolve(n), "x"))
      val out = RenameCmd.pruneRenamed(
        dir,
        List(
          Source.Alias(Source.Alias.Value("old2020_a"), Citekey("new1999_a")),
          Source.Alias(Source.Alias.Value("gone2020_c"), Citekey("new1999_c")), // no file: nothing to report
          Source.Alias(Source.Alias.Value("../keep2020_b"), Citekey("x2020_y"))
        )
      ) // not a citekey: never a path outside `dir`
      assertEquals(out, List(Right("old2020_a → new1999_a")))
      assert(!Files.exists(dir.resolve("old2020_a.md")))
      assert(Files.exists(dir.resolve("new1999_a.md")))
      assert(Files.exists(dir.resolve("keep2020_b.md")))
    finally
      Files.list(dir).forEach(f => Files.delete(f)); Files.delete(dir)
