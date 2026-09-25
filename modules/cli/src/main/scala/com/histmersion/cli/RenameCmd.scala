package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.core.*

import io.circe.syntax.*
import java.nio.file.{Files, Path}

/** `histmersion rename <old> <new>` — give a source a new citekey (`POST /api/sources/{old}/rename`).
  * The new key is checked HERE with the same pure rule the server applies ([[Rename.validate]]), so a
  * typo is a usage error before anything is sent. */
object RenameCmd:

  final case class Args(oldCitekey: String, newCitekey: String, json: Boolean)

  val args: Opts[Args] =
    (
      Opts.argument[String]("old"),
      Opts.argument[String]("new"),
      Opts.flag("json", "Print the raw JSON result instead of Markdown").orFalse
    ).mapN(Args.apply).mapValidated(a =>
      Rename.validate(a.oldCitekey, a.newCitekey).leftMap(_.message).toValidatedNel.map(ck =>
        a.copy(oldCitekey = a.oldCitekey.trim, newCitekey = ck)))

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("rename",
      "Give a source a new citekey — findings, links in articles/notes and every table follow; the old citekey keeps resolving as an alias") {
      args.map { a => (c: Client) =>
        c.renameSource(a.oldCitekey, a.newCitekey).map(r =>
          println(if a.json then r.asJson.spaces2 else Markdown.sourceRenamed(r)))
      }
    }

  /** Remove `<sources>/<alias>.md` for every old citekey — the mirror file a renamed source leaves
    * behind. `Right("old → new")` per file removed, `Left(reason)` per file that could not be; an
    * alias with no file is neither. Only ever touches `<alias>.md` directly inside `sources`. */
  def pruneRenamed(sources: Path, aliases: List[SourceAlias]): List[Either[String, String]] =
    aliases.filter(a => Citekey.valid(a.alias)).flatMap { a =>
      val f = sources.resolve(s"${a.alias}.md")
      scala.util.Try(Files.deleteIfExists(f)).fold(
        e => Some(Left(s"$f: ${e.getMessage}")),
        gone => Option.when(gone)(Right(s"${a.alias} → ${a.citekey}")))
    }
