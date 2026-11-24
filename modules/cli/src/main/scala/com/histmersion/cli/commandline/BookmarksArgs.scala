package com.histmersion
package cli
package commandline

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.personal.*
import com.histmersion.api.ItemKind
import com.histmersion.util.console.Console
import com.histmersion.api.library.Title
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*
import scala.util.chaining.*
import com.histmersion.lint.{BoundaryKind, atBoundary}

/** The command line of [[BookmarksCmd]]. */
@atBoundary(BoundaryKind.CliArgs, "the scallop adapter of `histmersion bookmarks`: declares its options, parses them into typed commands")
object BookmarksArgs:
  object BookmarksEntry extends CommandLine.Entry:
    val name = "bookmarks"
    def help: String = "Your bookmarks: list, add, rm"
    def conf(): Subcommand = GroupBookmarks()

  final private class LeafListCmd()
      extends Leaf("histmersion bookmarks", "list", "Your bookmarks, newest first")
      with SharedFlags:
    private val kind_ = value[String]("kind", s"Only: ${ItemKind.values.map(_.wire).mkString(", ")}")
    private val q_ = value[String]("query", "Words the bookmark's name or the item's title holds")
    private val limit_ = value[Int]("limit", s"How many (default ${PersonalIds.DefaultLimit})")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(
      BookmarksCmd.Command.ListCmd(kind_.toOption, q_.toOption, limit_.toOption, json_())
    )

  final private class LeafAdd()
      extends Leaf(
        "histmersion bookmarks",
        "add",
        "Bookmark an item (`source anthony2007_horse`), a place on the map " +
          "(`point --lat 37.73 --lon 22.75`), a view (`view --view 'from=-1300&to=-1100' --lat .. --lon .. --zoom 6`) or a " +
          "period (`period --from -1200 --to -1150 --label 'the crisis years'`)"
      )
      with SharedFlags:
    private val raw_ = targetFlags(ItemKind.Bookmarkable, TargetFlags.Views(true))
    private val label_ = value[String]("label", "Your name for it")
    private val title_ = value[String]("title", "What the item is called, when the collection does not hold it")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(
      BookmarksCmd.Command.Add(raw_(), label_.toOption, title_.toOption, json_())
    )

  final private class LeafRm()
      extends Leaf("histmersion bookmarks", "rm", "Remove a bookmark (its id, from `bookmarks list`)")
      with SharedFlags:
    private val id_ = arg[String]("id")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(BookmarksCmd.Command.Rm(id_(), json_()))

  final private class GroupBookmarks() extends Group("histmersion", "bookmarks", "Your bookmarks: list, add, rm"):
    holds(LeafListCmd(), LeafAdd(), LeafRm())
