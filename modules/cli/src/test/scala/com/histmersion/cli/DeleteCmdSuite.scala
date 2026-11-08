package com.histmersion
package cli

import com.histmersion.api.Problem
import com.histmersion.api.library.CalibreEntryCheck
import com.monovore.decline.Command
import com.histmersion.api.library.AuthorName
import com.histmersion.api.library.Source
import com.histmersion.config.CalibreCliConfig

/** `histmersion delete <citekey> [--apply] [--json]` (#969): a dry run unless `--apply`, no `--force`. */
class DeleteCmdSuite extends munit.FunSuite:

  private given console: com.histmersion.util.console.Console = com.histmersion.util.console.Console.standard

  private val cmd = Command("delete", "test")(DeleteCmd.args)
  private def parse(args: String*) = cmd.parse(args.toList, Map.empty)

  test("a dry run by default; --apply and --json are flags"):
    assertEquals(
      parse("page2024_history"),
      Right(
        DeleteCmd.Args(
          DeleteCmd.Args.Citekey("page2024_history"),
          apply = DeleteCmd.Args.Apply(false),
          json = DeleteCmd.Args.Json(false)
        )
      )
    )
    assertEquals(
      parse("page2024_history", "--apply"),
      Right(
        DeleteCmd.Args(
          DeleteCmd.Args.Citekey("page2024_history"),
          apply = DeleteCmd.Args.Apply(true),
          json = DeleteCmd.Args.Json(false)
        )
      )
    )
    assertEquals(
      parse("--json", " page2024_history "),
      Right(
        DeleteCmd.Args(
          DeleteCmd.Args.Citekey("page2024_history"),
          apply = DeleteCmd.Args.Apply(false),
          json = DeleteCmd.Args.Json(true)
        )
      )
    )

  test("no force override; missing arguments fail and citekeys reach server validation"):
    assert(parse("page2024_history", "--force").isLeft)
    assert(parse().isLeft)
    assertEquals(
      parse("Page 2024"),
      Right(
        DeleteCmd.Args(
          DeleteCmd.Args.Citekey("Page 2024"),
          apply = DeleteCmd.Args.Apply(false),
          json = DeleteCmd.Args.Json(false)
        )
      )
    )

  test("the subcommand is registered under `delete` and says it is a dry run"):
    val top = Command("histmersion", "test")(DeleteCmd.command)
    assert(top.parse(List("delete", "a2020_x"), Map.empty).isRight)
    assert(top.parse(List("delete", "--help"), Map.empty).left.exists(_.toString.contains("dry run")))

  test("the linked Calibre entry is looked up in the library before the answer says it is there (#1776)"):
    val book = Calibre.Book(
      Calibre.Book.Id(1885),
      Calibre.Book.Title("T"),
      List(AuthorName("A")),
      None,
      None,
      Nil,
      None,
      Map.empty,
      Nil
    )
    assertEquals(
      DeleteCmd
        .calibreCheck(Source.CalibreId("1885"), true, CalibreCliConfig.LibraryDir("/lib"), _ => Right(Some(book))),
      CalibreEntryCheck.Present
    )
    assertEquals(
      DeleteCmd.calibreCheck(Source.CalibreId("1885"), true, CalibreCliConfig.LibraryDir("/lib"), _ => Right(None)),
      CalibreEntryCheck.Missing
    )
    assertEquals(
      DeleteCmd.calibreCheck(
        Source.CalibreId("1885"),
        false,
        CalibreCliConfig.LibraryDir("/lib"),
        _ => fail("not read without a library")
      ),
      CalibreEntryCheck.Unchecked(Problem("no Calibre library at /lib"))
    )
    assertEquals(
      DeleteCmd.calibreCheck(
        Source.CalibreId("1885"),
        true,
        CalibreCliConfig.LibraryDir("/lib"),
        _ => Left(Problem("sqlite3: locked\nmore"))
      ),
      CalibreEntryCheck.Unchecked(Problem("sqlite3: locked"))
    )
    assert(
      DeleteCmd
        .calibreCheck(Source.CalibreId("x1"), true, CalibreCliConfig.LibraryDir("/lib"), _ => fail("not read"))
        .isInstanceOf[CalibreEntryCheck.Unchecked]
    )
