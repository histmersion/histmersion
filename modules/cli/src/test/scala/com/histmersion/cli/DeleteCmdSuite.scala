package com.histmersion.cli

import com.histmersion.api.library.CalibreEntryCheck
import com.monovore.decline.Command

/** `histmersion delete <citekey> [--apply] [--json]` (#969): a dry run unless `--apply`, no `--force`. */
class DeleteCmdSuite extends munit.FunSuite:

  private given console: com.histmersion.util.console.Console = com.histmersion.util.console.Console.standard

  private val cmd = Command("delete", "test")(DeleteCmd.args)
  private def parse(args: String*) = cmd.parse(args.toList, Map.empty)

  test("a dry run by default; --apply and --json are flags"):
    assertEquals(parse("page2024_history"), Right(DeleteCmd.Args(com.histmersion.cli.DeleteCmd.Args.Citekey("page2024_history"), apply = com.histmersion.cli.DeleteCmd.Args.Apply(false), json = com.histmersion.cli.DeleteCmd.Args.Json(false))))
    assertEquals(
      parse("page2024_history", "--apply"),
      Right(DeleteCmd.Args(com.histmersion.cli.DeleteCmd.Args.Citekey("page2024_history"), apply = com.histmersion.cli.DeleteCmd.Args.Apply(true), json = com.histmersion.cli.DeleteCmd.Args.Json(false)))
    )
    assertEquals(
      parse("--json", " page2024_history "),
      Right(DeleteCmd.Args(com.histmersion.cli.DeleteCmd.Args.Citekey("page2024_history"), apply = com.histmersion.cli.DeleteCmd.Args.Apply(false), json = com.histmersion.cli.DeleteCmd.Args.Json(true)))
    )

  test("no force override; missing arguments fail and citekeys reach server validation"):
    assert(parse("page2024_history", "--force").isLeft)
    assert(parse().isLeft)
    assertEquals(parse("Page 2024"), Right(DeleteCmd.Args(com.histmersion.cli.DeleteCmd.Args.Citekey("Page 2024"), apply = com.histmersion.cli.DeleteCmd.Args.Apply(false), json = com.histmersion.cli.DeleteCmd.Args.Json(false))))

  test("the subcommand is registered under `delete` and says it is a dry run"):
    val top = Command("histmersion", "test")(DeleteCmd.command)
    assert(top.parse(List("delete", "a2020_x"), Map.empty).isRight)
    assert(top.parse(List("delete", "--help"), Map.empty).left.exists(_.toString.contains("dry run")))

  test("the linked Calibre entry is looked up in the library before the answer says it is there (#1776)"):
    val book = Calibre.Book(com.histmersion.cli.Calibre.Book.Id(1885), com.histmersion.cli.Calibre.Book.Title("T"), List(com.histmersion.api.library.AuthorName("A")), None, None, Nil, None, Map.empty, Nil)
    assertEquals(DeleteCmd.calibreCheck(com.histmersion.api.library.Source.CalibreId("1885"), true, com.histmersion.config.CalibreCliConfig.LibraryDir("/lib"), _ => Right(Some(book))), CalibreEntryCheck.Present)
    assertEquals(DeleteCmd.calibreCheck(com.histmersion.api.library.Source.CalibreId("1885"), true, com.histmersion.config.CalibreCliConfig.LibraryDir("/lib"), _ => Right(None)), CalibreEntryCheck.Missing)
    assertEquals(
      DeleteCmd.calibreCheck(com.histmersion.api.library.Source.CalibreId("1885"), false, com.histmersion.config.CalibreCliConfig.LibraryDir("/lib"), _ => fail("not read without a library")),
      CalibreEntryCheck.Unchecked(com.histmersion.api.library.CalibreEntryCheck.Unchecked.Reason("no Calibre library at /lib"))
    )
    assertEquals(
      DeleteCmd.calibreCheck(com.histmersion.api.library.Source.CalibreId("1885"), true, com.histmersion.config.CalibreCliConfig.LibraryDir("/lib"), _ => Left(com.histmersion.cli.Shell.Problem("sqlite3: locked\nmore"))),
      CalibreEntryCheck.Unchecked(com.histmersion.api.library.CalibreEntryCheck.Unchecked.Reason("sqlite3: locked"))
    )
    assert(DeleteCmd.calibreCheck(com.histmersion.api.library.Source.CalibreId("x1"), true, com.histmersion.config.CalibreCliConfig.LibraryDir("/lib"), _ => fail("not read")).isInstanceOf[CalibreEntryCheck.Unchecked])
