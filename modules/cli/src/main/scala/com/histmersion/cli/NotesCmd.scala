package com.histmersion
package cli
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.api.personal.*

import java.nio.file.{Files, Path}
import com.histmersion.api.ItemKind
import com.histmersion.api.library.Title
import com.histmersion.util.console.Console
import com.histmersion.util.problem.Problems

/** `histmersion notes list|show|add|edit|rm` — your notes (Markdown), each left on an item or a place on the map.
  */
object NotesCmd:

  private def text(inline: Option[String], file: Option[String]): Either[Problems, Note.Text] =
    (inline, file) match
      case (Some(t), None) => Right(Note.Text(t))
      case (None, Some(f)) =>
        val p = Path.of(f)
        if Files.isRegularFile(p) then Right(Note.Text(Files.readString(p)))
        else Left(Problem.Unclassified(Legacy.Text(s"no such file: $f")))
      case _ =>
        Left(
          Problem.Unclassified(Legacy.Text("give the note's text with --text or --file (one of them)"))
        )

  /** What `histmersion …` of this group was asked to do, every argument parsed. */
  enum Command extends Cli.Command:
    case ListCmd(
        kind: Option[String],
        item: Option[String],
        q: Option[String],
        limit: Option[Int],
        json: Cli.JsonOutput
    )
    case Show(id: String, json: Cli.JsonOutput)
    case Add(
        raw: TargetFlags.Raw,
        inline: Option[String],
        file: Option[String],
        title: Option[String],
        json: Cli.JsonOutput
    )
    case Edit(id: String, inline: Option[String], file: Option[String], json: Cli.JsonOutput)
    case Rm(id: String, json: Cli.JsonOutput)

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = Perform(c)(this)

  /** Carries out Command commands against one server. */
  final private[cli] class Perform(c: Client)(using console: Console):
    def apply(cmd: Command): Either[Cli.Error, Unit] =
      cmd match
        case Command.ListCmd(kind, item, q, limit, json) =>
          c.call(PersonalApi.notes)(
            (kind.map(ItemKind.Wire(_)), item.map(ItemKey(_)), q.map(PersonalApi.Search(_)), limit)
          ).map(ns => Output.emit(json, ns)(PersonalMarkdown.notes))
        case Command.Show(id, json) =>
          c.call(PersonalApi.note)(Note.Id(id)).map(n => Output.emit(json, n)(PersonalMarkdown.note))
        case Command.Add(raw, inline, file, title, json) =>
          for
            t <- TargetFlags.target(raw)
            x <- text(inline, file).left.map(Cli.Error(_))
            n <- c.call(PersonalApi.addNote)(Note.New(t, x, title.map(Title(_))))
          yield Output.emit(json, n)(n => s"Noted on ${n.title} (id `${n.id.value}`).")
        case Command.Edit(id, inline, file, json) =>
          text(inline, file).left
            .map(Cli.Error(_))
            .flatMap(x => c.call(PersonalApi.editNote)((Note.Id(id), Note.Edit(x))))
            .map(n => Output.emit(json, n)(n => s"Changed the note on ${n.title}."))
        case Command.Rm(id, json) =>
          c.call(PersonalApi.removeNote)(Note.Id(id))
            .map(n => Output.emit(json, n)(n => s"Removed the note on ${n.title}."))
