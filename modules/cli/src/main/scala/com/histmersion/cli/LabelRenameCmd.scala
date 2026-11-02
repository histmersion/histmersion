package com.histmersion.cli

import com.histmersion.util.identity.AliasKinds

import com.histmersion.values.BundleKind

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.*
import com.histmersion.config.*

/** `histmersion article|events|goals rename <old> <new>` — a new slug or id; the old one keeps resolving (an alias).
  * The label is checked here with the server's own rule ([[LabelRenames]]).
  */
object LabelRenameCmd:

  def apply(kind: BundleKind): Opts[Client => Either[CliError, Unit]] =
    val (what, effect) = kind match
      case BundleKind.Article => ("an article a new slug", "versions, links and every [[old]] in other articles follow")
      case BundleKind.Event   => ("an event a new id", "attestations and asset links follow")
      case BundleKind.Place   => ("a place a new gazetteer id", "names, parents, events and finding assignments follow")
      case _                  => ("a goal a new slug", "sub-goals, links and schedules follow")
    Opts.subcommand("rename", s"Give $what — $effect. ${AliasKinds.of(kind).fold(Aliases.Rule)(Aliases.rule)}") {
      (Opts.argument[String]("old"), Opts.argument[String]("new"), Output.jsonFlag).tupled
        .mapValidated((from, to, json) =>
          LabelRenames.validate(kind, from, to).leftMap(_.message).toValidatedNel.map(t => (from.trim, t, json))
        )
        .map { (from, to, json) => (c: Client) =>
          val r = kind match
            case BundleKind.Article => c.renameArticle(from, to)
            case BundleKind.Event   => c.renameEvent(from, to)
            case BundleKind.Place   => c.renamePlace(from, to)
            case _                  => c.renameGoal(from, to)
          r.map(x => Output.emit(json, x)(MergeMarkdown.renamed))
        }
    }
