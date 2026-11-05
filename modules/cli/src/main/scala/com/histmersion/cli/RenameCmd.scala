package com.histmersion.cli

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.aliases.AliasKinds

import com.histmersion.api.library.SourcesMarkdown

import com.histmersion.domain.library.Citekey

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.config.*

import java.nio.file.{Files, Path}
import com.histmersion.api.aliases.Aliases
import com.histmersion.api.library.Rename
import com.histmersion.api.library.Source
import com.histmersion.util.console.Console

/** `histmersion rename <old> <new>` — give a source a new citekey (`POST /api/sources/{old}/rename`). The server
  * judges the new key and answers why it refuses one.
  */
object RenameCmd:

  type PruneRenamedResultPart1 = PruneRenamedResultPart1.T
  object PruneRenamedResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PruneRenamedResultPart2 = PruneRenamedResultPart2.T
  object PruneRenamedResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Args(oldCitekey: Args.OldCitekey, newCitekey: Args.NewCitekey, json: Args.Json)

  object Args:
    type OldCitekey = OldCitekey.T
    object OldCitekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NewCitekey = NewCitekey.T
    object NewCitekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Json = Json.T
    object Json extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val args: Opts[Args] =
    (Opts.argument[String]("old"), Opts.argument[String]("new"), Output.jsonFlag).mapN(Args.apply).map(a => a.modify(_.oldCitekey).setTo(a.oldCitekey.trim).modify(_.newCitekey).setTo(a.newCitekey.trim))

  def command(using console: Console) : Opts[Client => Either[Cli.Error, Unit]] =
    Opts.subcommand(
      "rename",
      s"Give a source a new citekey — findings, links in articles/notes and every table follow. ${Aliases.rule(AliasKinds.SourceRecords)}"
    ) {
      args.map { a => (c: Client) =>
        c.renameSource(a.oldCitekey, a.newCitekey).map(r => Output.emit(a.json, r)(SourcesMarkdown.sourceRenamed))
      }
    }

  /** Remove `<sources>/<alias>.md` for every old citekey — the mirror file a renamed source leaves behind.
    * `Right("old → new")` per file removed, `Left(reason)` per file that could not be; an alias with no file is
    * neither. Only ever touches `<alias>.md` directly inside `sources`.
    */
  def pruneRenamed(sources: Path, aliases: List[Source.Alias]): List[Either[com.histmersion.cli.RenameCmd.PruneRenamedResultPart1, com.histmersion.cli.RenameCmd.PruneRenamedResultPart2]] =
    aliases.filter(a => Citekey.valid(a.alias)).flatMap { a =>
      val f = sources.resolve(s"${a.alias}.md")
      scala.util
        .Try(Files.deleteIfExists(f))
        .fold(e => Some(Left(s"$f: ${e.getMessage}")), gone => Option.when(gone)(Right(s"${a.alias} → ${a.citekey}")))
    }
