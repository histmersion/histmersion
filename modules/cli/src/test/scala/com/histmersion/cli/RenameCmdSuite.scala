package com.histmersion.cli

import com.monovore.decline.Command

/** `histmersion rename <old> <new> [--json]`: the arguments parse, and a new citekey the server would refuse is a usage
  * error here, before any request.
  */
class RenameCmdSuite extends munit.FunSuite:

  private val cmd = Command("rename", "test")(RenameCmd.args)
  private def parse(args: String*) = cmd.parse(args.toList, Map.empty)

  test("two positional citekeys, --json optional"):
    assertEquals(
      parse("page2024_history", "page1959_history"),
      Right(RenameCmd.Args("page2024_history", "page1959_history", json = false))
    )
    assertEquals(
      parse("page2024_history", "page1959_history", "--json"),
      Right(RenameCmd.Args("page2024_history", "page1959_history", json = true))
    )

  test("the new citekey is trimmed"):
    assertEquals(
      parse("latacz2023_troy", " latacz2004_troy "),
      Right(RenameCmd.Args("latacz2023_troy", "latacz2004_troy", json = false))
    )

  test("a missing argument is a usage error"):
    assert(parse("page2024_history").isLeft)
    assert(parse().isLeft)

  test("an unusable new citekey is refused before anything is sent"):
    def says(args: List[String], text: String) =
      parse(args*) match
        case Left(help) => assert(help.errors.mkString.contains(text), help.errors.mkString)
        case Right(a)   => fail(s"accepted $a")
    says(List("page2024_history", "page2024_history"), "already the citekey")
    says(List("page2024_history", "Page 1959"), "invalid citekey")
    says(List("page2024_history", "page-1959"), "LINK-shaped")

  test("the subcommand is registered under `rename`"):
    val top = Command("histmersion", "test")(RenameCmd.command)
    assert(top.parse(List("rename", "a2020_x", "a1999_x"), Map.empty).isRight)
    assert(top.parse(List("rename", "--help"), Map.empty).left.exists(_.toString.contains("alias")))

  test("a full export removes the mirror notes of renamed citekeys, and nothing else"):
    import com.histmersion.api.library.SourceAlias
    import java.nio.file.Files
    val dir = Files.createTempDirectory("rename-prune")
    try
      List("old2020_a.md", "new1999_a.md", "keep2020_b.md").foreach(n => Files.writeString(dir.resolve(n), "x"))
      val out = RenameCmd.pruneRenamed(
        dir,
        List(
          SourceAlias("old2020_a", "new1999_a"),
          SourceAlias("gone2020_c", "new1999_c"), // no file: nothing to report
          SourceAlias("../keep2020_b", "x2020_y")
        )
      ) // not a citekey: never a path outside `dir`
      assertEquals(out, List(Right("old2020_a → new1999_a")))
      assert(!Files.exists(dir.resolve("old2020_a.md")))
      assert(Files.exists(dir.resolve("new1999_a.md")))
      assert(Files.exists(dir.resolve("keep2020_b.md")))
    finally
      Files.list(dir).forEach(f => Files.delete(f)); Files.delete(dir)
