package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.core.*

import io.circe.syntax.*

/** `histmersion delete <citekey> [--apply]` — delete a source (`POST /api/sources/{ck}/delete`, #969).
  *
  * A DRY RUN by default: it prints what would go and what refuses it. `--apply` deletes in one
  * transaction. There is deliberately no `--force`: an article citing the source has to be fixed
  * first. Calibre is never touched; the linked Calibre id is printed so a person can decide. */
object DeleteCmd:

  final case class Args(citekey: String, apply: Boolean, json: Boolean)

  val args: Opts[Args] =
    (
      Opts.argument[String]("citekey"),
      Opts.flag("apply", "Delete it (without this flag, a dry run lists what would go and what refuses it)").orFalse,
      Opts.flag("json", "Print the raw JSON result instead of Markdown").orFalse
    ).mapN((ck, apply, json) => Args(ck.trim, apply, json))
      .mapValidated(a => Citekey.of(a.citekey).leftMap(_.message).toValidatedNel.map(_ => a))

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("delete",
      "Delete a source with its findings, edges, figures and stored original — a dry run unless --apply; refused while an article cites it; Calibre is never touched") {
      args.map(a => (c: Client) => run(c, a))
    }

  def run(c: Client, a: Args): Either[CliError, Unit] =
    c.deleteSource(a.citekey, a.apply).flatMap { d =>
      println(if a.json then d.asJson.spaces2 else SourceDelete.markdown(d))
      // a refused dry run is not a success: a script that goes on to `--apply` would fail anyway
      if d.refused then Left(CliError(SourceDelete.refusal(d.citekey, d.blockers), exitCode = 2))
      else if d.filesFailed.nonEmpty then
        Left(CliError(s"deleted, but ${Plural.count(d.filesFailed.size, "stored file")} could not be removed", exitCode = 2))
      else Right(())
    }
