package com.histmersion.cli

import com.histmersion.core.CalibreEntryCheck
import com.monovore.decline.Command

/** `histmersion delete <citekey> [--apply] [--json]` (#969): a dry run unless `--apply`, no `--force`. */
class DeleteCmdSuite extends munit.FunSuite:

  private val cmd = Command("delete", "test")(DeleteCmd.args)
  private def parse(args: String*) = cmd.parse(args.toList, Map.empty)

  test("a dry run by default; --apply and --json are flags"):
    assertEquals(parse("page2024_history"), Right(DeleteCmd.Args("page2024_history", apply = false, json = false)))
    assertEquals(parse("page2024_history", "--apply"), Right(DeleteCmd.Args("page2024_history", apply = true, json = false)))
    assertEquals(parse("--json", " page2024_history "), Right(DeleteCmd.Args("page2024_history", apply = false, json = true)))

  test("there is no override for a cited source, and a bad citekey is a usage error"):
    assert(parse("page2024_history", "--force").isLeft)
    assert(parse().isLeft)
    assert(parse("Page 2024").left.exists(_.errors.mkString.contains("invalid citekey")))

  test("the subcommand is registered under `delete` and says it is a dry run"):
    val top = Command("histmersion", "test")(DeleteCmd.command)
    assert(top.parse(List("delete", "a2020_x"), Map.empty).isRight)
    assert(top.parse(List("delete", "--help"), Map.empty).left.exists(_.toString.contains("dry run")))

  test("the linked Calibre entry is looked up in the library before the answer says it is there (#1776)"):
    val book = CalibreBook(1885, "T", List("A"), None, None, Nil, None, Map.empty, Nil)
    assertEquals(DeleteCmd.calibreCheck("1885", true, "/lib", _ => Right(Some(book))), CalibreEntryCheck.Present)
    assertEquals(DeleteCmd.calibreCheck("1885", true, "/lib", _ => Right(None)), CalibreEntryCheck.Missing)
    assertEquals(DeleteCmd.calibreCheck("1885", false, "/lib", _ => fail("not read without a library")),
      CalibreEntryCheck.Unchecked("no Calibre library at /lib"))
    assertEquals(DeleteCmd.calibreCheck("1885", true, "/lib", _ => Left("sqlite3: locked\nmore")),
      CalibreEntryCheck.Unchecked("sqlite3: locked"))
    assert(DeleteCmd.calibreCheck("x1", true, "/lib", _ => fail("not read")).isInstanceOf[CalibreEntryCheck.Unchecked])
