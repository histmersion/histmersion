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
  type Message = Message.T
  object Message extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Args(oldCitekey: Args.OldCitekey, newCitekey: Args.NewCitekey, json: Args.Json)

  object Args:
    type OldCitekey = com.histmersion.plugin.FetchedFile.Source.Citekey
    val OldCitekey: _root_.com.histmersion.plugin.FetchedFile.Source.Citekey.type = _root_.com.histmersion.plugin.FetchedFile.Source.Citekey
    type NewCitekey = com.histmersion.plugin.FetchedFile.Source.Citekey
    val NewCitekey: _root_.com.histmersion.plugin.FetchedFile.Source.Citekey.type = _root_.com.histmersion.plugin.FetchedFile.Source.Citekey
    type Json = com.histmersion.cli.Cli.JsonOutput
    val Json: _root_.com.histmersion.cli.Cli.JsonOutput.type = _root_.com.histmersion.cli.Cli.JsonOutput

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
  def pruneRenamed(sources: Path, aliases: List[Source.Alias]): List[Either[com.histmersion.cli.RenameCmd.Problem, com.histmersion.cli.RenameCmd.Message]] =
    aliases.filter(a => Citekey.valid(a.alias)).flatMap { a =>
      val f = sources.resolve(s"${a.alias}.md")
      scala.util
        .Try(Files.deleteIfExists(f))
        .fold(e => Some(Left(s"$f: ${e.getMessage}")), gone => Option.when(gone)(Right(s"${a.alias} → ${a.citekey}")))
    }
