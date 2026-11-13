package com.histmersion
package cli
package commandline

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.api.personal.*
import java.nio.file.{Files, Path}
import com.histmersion.api.ItemKind
import com.histmersion.api.library.Title
import com.histmersion.util.console.Console
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*
import scala.util.chaining.*

/** The command line of [[NotesCmd]]. */
object NotesArgs:
  object NotesEntry extends CommandLine.Entry:
    val name = "notes"
    def help: String = "Your notes: list, show, add, edit, rm"
    def conf(): Subcommand = GroupNotes()

  final private class LeafListCmd()
      extends Leaf("histmersion notes", "list", "Your notes, last changed first")
      with SharedFlags:
    private val kind_ = value[String](
      "kind",
      s"Only those left on: ${ItemKind.values.filter(ItemKind.Notable).map(_.wire).mkString(", ")}"
    )
    private val item_ = value[String]("item", "Only those left on this item (a citekey, a slug, an id)")
    private val q_ = value[String]("query", "Words the note or the item's title holds")
    private val limit_ = value[Int]("limit", s"How many (default ${PersonalIds.DefaultLimit})")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(
      NotesCmd.Command.ListCmd(kind_.toOption, item_.toOption, q_.toOption, limit_.toOption, json_())
    )

  final private class LeafShow() extends Leaf("histmersion notes", "show", "One note in full") with SharedFlags:
    private val id_ = arg[String]("id")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(NotesCmd.Command.Show(id_(), json_()))

  final private class LeafAdd()
      extends Leaf(
        "histmersion notes",
        "add",
        "Leave a note on an item (`source anthony2007_horse --text …`) or on a " +
          "place on the map (`point --lat 37.73 --lon 22.75 [--from -1300 --to -1100] --text …`)"
      )
      with SharedFlags:
    private val raw_ = targetFlags(ItemKind.Notable, TargetFlags.Views(false))
    private val inline_ = value[String]("text", "The note (Markdown)")
    private val file_ = value[String]("file", "Read the note from this Markdown file")
    private val title_ = value[String]("title", "What the item is called, when the collection does not hold it")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(
      NotesCmd.Command.Add(raw_(), inline_.toOption, file_.toOption, title_.toOption, json_())
    )

  final private class LeafEdit() extends Leaf("histmersion notes", "edit", "Replace a note's text") with SharedFlags:
    private val id_ = arg[String]("id")
    private val inline_ = value[String]("text", "The note (Markdown)")
    private val file_ = value[String]("file", "Read the note from this Markdown file")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(
      NotesCmd.Command.Edit(id_(), inline_.toOption, file_.toOption, json_())
    )

  final private class LeafRm()
      extends Leaf("histmersion notes", "rm", "Remove a note (its id, from `notes list`)")
      with SharedFlags:
    private val id_ = arg[String]("id")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(NotesCmd.Command.Rm(id_(), json_()))

  final private class GroupNotes() extends Group("histmersion", "notes", "Your notes: list, show, add, edit, rm"):
    holds(LeafListCmd(), LeafShow(), LeafAdd(), LeafEdit(), LeafRm())
