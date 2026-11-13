package com.histmersion
package cli
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import com.histmersion.api.Plural
import com.histmersion.config.*
import com.histmersion.api.library.CalibreEntryCheck
import com.histmersion.api.library.Source
import com.histmersion.util.console.Console
import com.histmersion.api.Api
import com.histmersion.config.CalibreCliConfig

/** `histmersion delete <citekey> [--apply]` — delete a source (`POST /api/sources/{ck}/delete`, #969).
  *
  * A DRY RUN by default: it prints what would go and what refuses it. `--apply` deletes in one transaction. There is
  * deliberately no `--force`: an article citing the source has to be fixed first. Calibre is never touched; the linked
  * Calibre id is printed so a person can decide, after a read-only look in the library says whether the entry is still
  * there.
  */
object DeleteCmd:
  final case class Args(citekey: Args.Citekey, apply: Args.Apply, json: Args.Json) extends Cli.Command:
    def run(c: Client)(using Console): Either[Cli.Error, Unit] = Perform(c)(this)

  object Args:
    type Citekey = com.histmersion.api.library.Citekey
    val Citekey: com.histmersion.api.library.Citekey.type = com.histmersion.api.library.Citekey
    type Apply = Api.Apply
    val Apply: Api.Apply.type = Api.Apply
    type Json = Cli.JsonOutput
    val Json: Cli.JsonOutput.type = Cli.JsonOutput

  val Help: String =
    "Delete a source with its findings, edges, figures and stored original — a dry run unless --apply; refused while an article cites it; Calibre is never touched"

  /** The linked entry as the host's library has it (#1776), read-only: the record's `calibreId` may name an entry a
    * person has deleted since. Not a number, or no library: unchecked, with why.
    */
  def calibreCheck(
      calibreId: Source.CalibreId,
      libraryExists: => Boolean,
      library: CalibreCliConfig.LibraryDir,
      byId: Calibre.Book.Id => Either[Problem, Option[Calibre.Book]]
  ): CalibreEntryCheck =
    calibreId.value.trim.toIntOption match
      case None => CalibreEntryCheck.Unchecked(Problem.Unclassified(Legacy.Text(s"'$calibreId' is not a Calibre id")))
      case Some(_) if !libraryExists =>
        CalibreEntryCheck.Unchecked(Problem.Unclassified(Legacy.Text(s"no Calibre library at $library")))
      case Some(id) =>
        byId(Calibre.Book.Id(id)) match
          case Right(Some(_)) => CalibreEntryCheck.Present
          case Right(None)    => CalibreEntryCheck.Missing
          case Left(e)        =>
            CalibreEntryCheck.Unchecked(
              Problem.Unclassified(Legacy.Text(e.sentence.linesIterator.nextOption().getOrElse(e.sentence).take(200)))
            )

  /** Carries out Args commands against one server. */
  final private[cli] class Perform(c: Client)(using console: Console):
    def apply(a: Args): Either[Cli.Error, Unit] =
      c.deleteSource(a.citekey, a.apply).flatMap { d =>
        lazy val check = d.calibreId.map { id =>
          val cfg = Settings.app.cli.calibre
          val lib = Calibre(cfg)
          calibreCheck(id, lib.libraryExists, cfg.library, lib.byId)
        }
        Output.emit(a.json, d)(Source.Delete.markdown(_, check).value)
        // a refused dry run is not a success: a script that goes on to `--apply` would fail anyway
        if d.refused then
          Left(
            Cli.Error(Source.Delete.refusal(d.citekey, d.blockers), exitCode = ExitCode.Refused)
          )
        else if d.filesFailed.nonEmpty then
          Left(
            Cli.Error(
              Problem.Unclassified(
                Legacy.Text(
                  s"deleted, but ${Plural.count(Plural.CountN(d.filesFailed.size.toLong), Plural.CountSingular("stored file"))} could not be removed"
                )
              ),
              exitCode = ExitCode.Refused
            )
          )
        else Right(())
      }
