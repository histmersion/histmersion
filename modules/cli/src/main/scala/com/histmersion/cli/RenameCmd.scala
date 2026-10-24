package com.histmersion.cli

import com.histmersion.values.Citekey

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

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
      Output.jsonFlag
    ).mapN(Args.apply).mapValidated(a =>
      Rename.validate(a.oldCitekey, a.newCitekey).leftMap(_.message).toValidatedNel.map(ck =>
        a.copy(oldCitekey = a.oldCitekey.trim, newCitekey = ck.value)))

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("rename",
      s"Give a source a new citekey — findings, links in articles/notes and every table follow. ${Aliases.rule(AliasKind.Source)}") {
      args.map { a => (c: Client) =>
        c.renameSource(a.oldCitekey, a.newCitekey).map(r =>
          Output.emit(a.json, r)(Markdown.sourceRenamed))
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

/** `histmersion article|events|goals rename <old> <new>` — a new slug or id; the old one keeps
  * resolving (an alias). The label is checked here with the server's own rule ([[LabelRenames]]). */
object LabelRenameCmd:

  def apply(kind: BundleKind): Opts[Client => Either[CliError, Unit]] =
    val (what, effect) = kind match
      case BundleKind.Article => ("an article a new slug", "versions, links and every [[old]] in other articles follow")
      case BundleKind.Event   => ("an event a new id", "attestations and asset links follow")
      case BundleKind.Place   => ("a place a new gazetteer id", "names, parents, events and finding assignments follow")
      case _                  => ("a goal a new slug", "sub-goals, links and schedules follow")
    Opts.subcommand("rename", s"Give $what — $effect. ${AliasKind.of(kind).fold(Aliases.Rule)(Aliases.rule)}") {
      (Opts.argument[String]("old"), Opts.argument[String]("new"),
        Output.jsonFlag)
        .tupled.mapValidated((from, to, json) =>
          LabelRenames.validate(kind, from, to).leftMap(_.message).toValidatedNel.map(t => (from.trim, t, json)))
        .map { (from, to, json) => (c: Client) =>
          val r = kind match
            case BundleKind.Article => c.renameArticle(from, to)
            case BundleKind.Event   => c.renameEvent(from, to)
            case BundleKind.Place   => c.renamePlace(from, to)
            case _                  => c.renameGoal(from, to)
          r.map(x => Output.emit(json, x)(MergeMarkdown.renamed))
        }
    }
