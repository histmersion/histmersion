package com.histmersion
package server

import java.nio.file.Path
import scala.concurrent.duration.Duration

import comments.CommentScope

class CommentGrowthSuite extends munit.FunSuite:
  import CommentGrowthSuite.*

  override val munitTimeout = Duration(5, "min")

  test(
    "G22: no file has more comments than at the merge base with the pilot, and a file the base does not have has none"
  ):
    val git = GitRepo(Path.of("."))
    git.base(sys.env) match
      case Left(why)   => fail(why)
      case Right(base) =>
        val changed = changedSince(git, base.commit)
        val renamed = renames(git.ok("diff", "-M", "--name-status", base.commit).getOrElse(""))
        val counts = CommentScope.default
          .files(Path.of("."))
          .filter(f => f.counted.nonEmpty && changed(f.path))
          .map(f => f.path -> f.counted.size)
        def atBase(path: String): Int =
          val before = renamed.getOrElse(path, path)
          git
            .show(base.commit, before)
            .flatMap(lines => CommentScope.default.parsed(before, lines.mkString("\n")))
            .fold(0)(_.counted.size)
        val grown = growth(counts.toList, atBase)
        assert(
          grown.isEmpty,
          s"comments added since ${base.describe} (G22: code carries no comments; a design decision goes to an ADR in " +
            "docs/adr/, a shortcut to an issue; see .claude/skills/delete-comment):\n" +
            grown.map((path, was, now) => s"  $path: $was -> $now").mkString("\n")
        )

  test("G22: a file whose comments outnumber its base's grows; a new file counts zero at the base"):
    val counts = List("a.scala" -> 2, "b.scala" -> 3, "new.sh" -> 1)
    val base = Map("a.scala" -> 2, "b.scala" -> 1)
    assertEquals(growth(counts, base.getOrElse(_, 0)), List(("b.scala", 1, 3), ("new.sh", 0, 1)))

  test("G22: a renamed file is compared with its old path"):
    val status = "R087\tmodules/a/Old.scala\tmodules/b/New.scala\nM\tbuild.sbt\nA\tx.sh\n"
    assertEquals(renames(status), Map("modules/b/New.scala" -> "modules/a/Old.scala"))

object CommentGrowthSuite:
  def growth(counts: List[(String, Int)], atBase: String => Int): List[(String, Int, Int)] =
    counts
      .map((path, now) => (path, atBase(path), now))
      .filter((_, was, now) => now > was)
      .sortBy(_._1)

  def renames(nameStatus: String): Map[String, String] =
    nameStatus.linesIterator
      .map(_.split("\t").toList)
      .collect { case status :: from :: to :: Nil if status.startsWith("R") => to -> from }
      .toMap

  def changedSince(git: GitRepo, commit: String): Set[String] =
    (git.ok("diff", "--name-only", commit).getOrElse("") + "\n" +
      git.ok("ls-files", "--others", "--exclude-standard").getOrElse("")).linesIterator.filter(_.nonEmpty).toSet
