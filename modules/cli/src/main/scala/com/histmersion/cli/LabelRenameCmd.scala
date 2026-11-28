package com.histmersion
package cli

import com.histmersion.api.aliases.AliasKind

import com.histmersion.util.problem.Text.sentence
import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.api.exchange.Bundle

import com.histmersion.api.aliases.AliasKinds

import com.histmersion.config.*
import com.histmersion.api.aliases.LabelRename
import com.histmersion.api.automation.Goal
import com.histmersion.api.catalogue.{Event, Place}
import com.histmersion.api.research.ReviewGoal
import com.histmersion.api.exchange.MergeMarkdown
import com.histmersion.util.console.Console
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** `histmersion article|events|goals rename <old> <new>` — a new slug or id; the old one keeps resolving (an alias).
  * The label is checked here with the server's own rule ([[LabelRenames]]).
  */
@atBoundary(BoundaryKind.CliArgs, "parses `histmersion label-rename` arguments")
object LabelRenameCmd:

  /** `<kind> rename <old> <new>`, the new label already checked; `from` trimmed. */
  final case class Rename(kind: Bundle.Kind, from: String, to: LabelRename.To, json: Cli.JsonOutput)
      extends Cli.Command:
    def run(c: Client)(using Console): Either[Cli.Error, Unit] =
      val r = kind match
        case Bundle.Kind.Article => c.renameArticle(ReviewGoal.Slug(from), to)
        case Bundle.Kind.Event   => c.renameEvent(Event.Uid(from), to)
        case Bundle.Kind.Place   => c.renamePlace(Place.Id(from), to)
        case _                   => c.renameGoal(Goal.Uid(from), to)
      r.map(x => Output.emit(json, x)(MergeMarkdown.renamed))

  /** The rename `kind` asks for, or why the new label is refused. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def rename(kind: Bundle.Kind, from: String, to: String, json: Cli.JsonOutput): Either[Problems, Rename] =
    LabelRename
      .validate(kind, LabelRename.Label(from), LabelRename.Label(to))
      .left
      .map(_.problems)
      .map(t => Rename(kind, from.trim, LabelRename.To(t.value), json))

  /** The help line of the `rename` subcommand of `kind`. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def help(kind: Bundle.Kind): String =
    val (what, effect) = kind match
      case Bundle.Kind.Article =>
        ("an article a new slug", "versions, links and every [[old]] in other articles follow")
      case Bundle.Kind.Event => ("an event a new id", "attestations and asset links follow")
      case Bundle.Kind.Place => ("a place a new gazetteer id", "names, parents, events and finding assignments follow")
      case _                 => ("a goal a new slug", "sub-goals, links and schedules follow")
    s"Give $what — $effect. ${AliasKinds.of(kind).fold(AliasKind.Rule)(_.rule)}"
