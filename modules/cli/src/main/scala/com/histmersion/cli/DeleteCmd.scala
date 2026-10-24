package com.histmersion.cli

import com.histmersion.values.Citekey

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*


/** `histmersion delete <citekey> [--apply]` — delete a source (`POST /api/sources/{ck}/delete`, #969).
  *
  * A DRY RUN by default: it prints what would go and what refuses it. `--apply` deletes in one
  * transaction. There is deliberately no `--force`: an article citing the source has to be fixed
  * first. Calibre is never touched; the linked Calibre id is printed so a person can decide, after a
  * read-only look in the library says whether the entry is still there. */
object DeleteCmd:

  final case class Args(citekey: String, apply: Boolean, json: Boolean)

  val args: Opts[Args] =
    (
      Opts.argument[String]("citekey"),
      Opts.flag("apply", "Delete it (without this flag, a dry run lists what would go and what refuses it)").orFalse,
      Output.jsonFlag
    ).mapN((ck, apply, json) => Args(ck.trim, apply, json))
      .mapValidated(a => Citekey.of(a.citekey).leftMap(_.message).toValidatedNel.map(_ => a))

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("delete",
      "Delete a source with its findings, edges, figures and stored original — a dry run unless --apply; refused while an article cites it; Calibre is never touched") {
      args.map(a => (c: Client) => run(c, a))
    }

  /** The linked entry as the host's library has it (#1776), read-only: the record's `calibreId` may
    * name an entry a person has deleted since. Not a number, or no library: unchecked, with why. */
  def calibreCheck(calibreId: String, libraryExists: => Boolean, library: String,
      byId: Int => Either[String, Option[CalibreBook]]): CalibreEntryCheck =
    calibreId.trim.toIntOption match
      case None                       => CalibreEntryCheck.Unchecked(s"'$calibreId' is not a Calibre id")
      case Some(_) if !libraryExists  => CalibreEntryCheck.Unchecked(s"no Calibre library at $library")
      case Some(id) =>
        byId(id) match
          case Right(Some(_)) => CalibreEntryCheck.Present
          case Right(None)    => CalibreEntryCheck.Missing
          case Left(e)        => CalibreEntryCheck.Unchecked(e.linesIterator.nextOption().getOrElse(e).take(200))

  def run(c: Client, a: Args): Either[CliError, Unit] =
    c.deleteSource(a.citekey, a.apply).flatMap { d =>
      lazy val check = d.calibreId.map { id =>
        val cfg = Settings.app.cli.calibre
        val lib = Calibre(cfg)
        calibreCheck(id, lib.libraryExists, cfg.library, lib.byId)
      }
      Output.emit(a.json, d)(SourceDelete.markdown(_, check))
      // a refused dry run is not a success: a script that goes on to `--apply` would fail anyway
      if d.refused then Left(CliError(SourceDelete.refusal(d.citekey, d.blockers), exitCode = ExitCode.Refused))
      else if d.filesFailed.nonEmpty then
        Left(CliError(s"deleted, but ${Plural.count(d.filesFailed.size, "stored file")} could not be removed", exitCode = ExitCode.Refused))
      else Right(())
    }
