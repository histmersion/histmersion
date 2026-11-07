package com.histmersion.cli

import com.histmersion.api.exchange.Bundle

import com.histmersion.api.aliases.AliasKinds

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.config.*
import com.histmersion.api.aliases.{Aliases, LabelRename, LabelRenames}
import com.histmersion.api.automation.Goal
import com.histmersion.api.catalogue.{Event, Place}
import com.histmersion.api.research.ReviewGoal
import com.histmersion.api.exchange.MergeMarkdown
import com.histmersion.util.console.Console

/** `histmersion article|events|goals rename <old> <new>` — a new slug or id; the old one keeps resolving (an alias).
  * The label is checked here with the server's own rule ([[LabelRenames]]).
  */
object LabelRenameCmd:

  def apply(kind: Bundle.Kind)(using console: Console) : Opts[Client => Either[Cli.Error, Unit]] =
    val (what, effect) = kind match
      case Bundle.Kind.Article =>
        ("an article a new slug", "versions, links and every [[old]] in other articles follow")
      case Bundle.Kind.Event => ("an event a new id", "attestations and asset links follow")
      case Bundle.Kind.Place => ("a place a new gazetteer id", "names, parents, events and finding assignments follow")
      case _                 => ("a goal a new slug", "sub-goals, links and schedules follow")
    Opts.subcommand("rename", s"Give $what — $effect. ${AliasKinds.of(kind).fold(Aliases.Rule)(Aliases.rule)}") {
      (Opts.argument[String]("old"), Opts.argument[String]("new"), Output.jsonFlag).tupled
        .mapValidated((from, to, json) =>
          LabelRenames
            .validate(kind, LabelRenames.Label(from), LabelRenames.Label(to))
            .leftMap(_.message.value)
            .toValidatedNel
            .map(t => (from.trim, LabelRename.To(t.value), json))
        )
        .map { (from, to, json) => (c: Client) =>
          val r = kind match
            case Bundle.Kind.Article => c.renameArticle(ReviewGoal.Slug(from), to)
            case Bundle.Kind.Event   => c.renameEvent(Event.Uid(from), to)
            case Bundle.Kind.Place   => c.renamePlace(Place.Id(from), to)
            case _                   => c.renameGoal(Goal.Uid(from), to)
          r.map(x => Output.emit(json, x)(MergeMarkdown.renamed))
        }
    }
