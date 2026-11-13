package com.histmersion
package cli
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.problem.{Legacy, Problem}

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.personal.*

import java.nio.file.{Files, Path}
import com.histmersion.api.ItemKind
import com.histmersion.api.library.Title
import com.histmersion.util.console.Console

/** `histmersion notes list|show|add|edit|rm` — your notes (Markdown), each left on an item or a place on the map.
  */
object NotesCmd:

  private def list(using console: Console) = Opts.subcommand("list", "Your notes, last changed first") {
    (
      Opts
        .option[String](
          "kind",
          s"Only those left on: ${ItemKind.values.filter(ItemKind.Notable).map(_.wire).mkString(", ")}"
        )
        .orNone,
      Opts.option[String]("item", "Only those left on this item (a citekey, a slug, an id)").orNone,
      Opts.option[String]("query", "Words the note or the item's title holds").orNone,
      Opts.option[Int]("limit", s"How many (default ${PersonalIds.DefaultLimit})").orNone,
      Output.jsonFlag
    ).mapN { (kind, item, q, limit, json) => (c: Client) =>
      c.call(PersonalApi.notes)((kind.map(ItemKind.Wire(_)), item.map(ItemKey(_)), q.map(PersonalApi.Search(_)), limit))
        .map(ns => Output.emit(json, ns)(PersonalMarkdown.notes))
    }
  }

  private def show(using console: Console) = Opts.subcommand("show", "One note in full") {
    (Opts.argument[String]("id"), Output.jsonFlag).mapN { (id, json) => (c: Client) =>
      c.call(PersonalApi.note)(Note.Id(id)).map(n => Output.emit(json, n)(PersonalMarkdown.note))
    }
  }

  private def text(inline: Option[String], file: Option[String]): Either[Cli.Error, Note.Text] =
    (inline, file) match
      case (Some(t), None) => Right(Note.Text(t))
      case (None, Some(f)) =>
        val p = Path.of(f)
        if Files.isRegularFile(p) then Right(Note.Text(Files.readString(p)))
        else Left(Cli.Error(Problem.Unclassified(Legacy.Text(s"no such file: $f")), exitCode = ExitCode.Usage))
      case _ =>
        Left(
          Cli.Error(
            Problem.Unclassified(Legacy.Text("give the note's text with --text or --file (one of them)")),
            exitCode = ExitCode.Usage
          )
        )

  private val textOpts = (
    Opts.option[String]("text", "The note (Markdown)").orNone,
    Opts.option[String]("file", "Read the note from this Markdown file").orNone
  )

  private def add(using console: Console) = Opts.subcommand(
    "add",
    "Leave a note on an item (`source anthony2007_horse --text …`) or on a " +
      "place on the map (`point --lat 37.73 --lon 22.75 [--from -1300 --to -1100] --text …`)"
  ) {
    (
      TargetFlags.opts(ItemKind.Notable, views = TargetFlags.Views(false)),
      textOpts._1,
      textOpts._2,
      Opts.option[String]("title", "What the item is called, when the collection does not hold it").orNone,
      Output.jsonFlag
    ).mapN { (raw, inline, file, title, json) => (c: Client) =>
      for
        t <- TargetFlags.target(raw)
        x <- text(inline, file)
        n <- c.call(PersonalApi.addNote)(Note.New(t, x, title.map(Title(_))))
      yield Output.emit(json, n)(n => s"Noted on ${n.title} (id `${n.id.value}`).")
    }
  }

  private def edit(using console: Console) = Opts.subcommand("edit", "Replace a note's text") {
    (Opts.argument[String]("id"), textOpts._1, textOpts._2, Output.jsonFlag).mapN {
      (id, inline, file, json) => (c: Client) =>
        text(inline, file)
          .flatMap(x => c.call(PersonalApi.editNote)((Note.Id(id), Note.Edit(x))))
          .map(n => Output.emit(json, n)(n => s"Changed the note on ${n.title}."))
    }
  }

  private def rm(using console: Console) = Opts.subcommand("rm", "Remove a note (its id, from `notes list`)") {
    (Opts.argument[String]("id"), Output.jsonFlag).mapN { (id, json) => (c: Client) =>
      c.call(PersonalApi.removeNote)(Note.Id(id))
        .map(n => Output.emit(json, n)(n => s"Removed the note on ${n.title}."))
    }
  }

  def command(using console: Console): Opts[Client => Either[Cli.Error, Unit]] =
    Opts.subcommand("notes", "Your notes: list, show, add, edit, rm")(list orElse show orElse add orElse edit orElse rm)
