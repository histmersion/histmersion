package com.histmersion.cli

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.annotation.tailrec

/** The help of every command and subcommand, as `--help` prints it (the text `tools/gen-cli-docs.sh` renders), compared
  * byte for byte with `src/test/resources/contract/cli-help.txt`. An intended change rewrites the snapshot with
  * `HISTMERSION_UPDATE_CONTRACT=1` and shows in the diff.
  */
class CliHelpSnapshotSuite extends munit.FunSuite:

  private val file = Path.of("modules/cli/src/test/resources/contract/cli-help.txt")

  private def help(path: List[String]): String =
    Main.command.parse(path :+ "--help", Map.empty).fold(_.toString, _ => s"(no help for ${path.mkString(" ")})")

  private def subcommands(text: String): List[String] =
    text.linesIterator
      .dropWhile(l => !l.startsWith("Subcommands:"))
      .drop(1)
      .collect { case l if l.matches("    [a-z][a-z0-9-]*") => l.trim }
      .toList

  /** Every command path in the tree, depth first, each with its help. */
  private def tree: List[(List[String], String)] =
    @tailrec def loop(todo: List[List[String]], done: List[(List[String], String)]): List[(List[String], String)] =
      todo match
        case Nil          => done.reverse
        case path :: rest =>
          val text = help(path)
          loop(subcommands(text).map(path :+ _) ++ rest, (path, text) :: done)
    loop(List(Nil), Nil)

  test("cli-help.txt: the help of every command"):
    val text = tree
      .map((path, h) => s"==== histmersion ${path.mkString(" ")}".trim + "\n" + h.stripTrailing + "\n")
      .mkString("\n")
    if sys.env.get("HISTMERSION_UPDATE_CONTRACT").contains("1") then
      Files.createDirectories(file.getParent)
      val _ = Files.writeString(file, text, UTF_8)
    else
      assert(Files.exists(file), s"$file is missing; write it with HISTMERSION_UPDATE_CONTRACT=1")
      assert(Files.readString(file, UTF_8) == text, "the CLI help differs from the snapshot")
