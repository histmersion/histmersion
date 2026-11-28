package com.histmersion
package api.automation

import com.histmersion.api.automation.managers.ManagerItem
import com.histmersion.api.automation.managers.ManagerInfo

import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.util.problem.Problem.{Field, Invalid, Raw, TypeName}
import com.histmersion.util.problem.Text.sentence

import scala.annotation.tailrec
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.research.Article
import com.histmersion.api.WireEnumValues
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Doi
import com.histmersion.api.library.Title
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.AuthorName
import com.histmersion.api.library.Source
import com.histmersion.api.ItemKind
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.Wording
import com.histmersion.api.Paging
import com.histmersion.api.json.TwoStates

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
object Managers:
  /** Why a manager proposes, holds or changes an item: the narration of a decision, not a failure. */
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)

  object Usd:
    type Amount = Amount.T
    object Amount extends Newtype[Double, Pure]
  object Scheduler:
    export Paging.Limit

  type Actions = ActionsValue
  type ActionsValue = ActionsValue.T
  object ActionsValue extends Newtype[String, Pure]

  export Manager.Kind as Kind
  export Manager.State as State
  export com.histmersion.api.automation.ItemStatus

  object Feed:
    val Started = "started"
    val Settled = "settled"
    val Evaluated = "evaluated"
    val Stopped = "stopped"
    val Escalated = "escalated"
    val Held = "held"
    val Hint = "hint"
    val Paused = "paused"
    val Stall = "stall"
    val Starved = "starved"

    val Swept = "swept"

    val Ingested = "ingested"

    val Cron = "cron"

    val Migrated = "migrated"

    val Approval = "approval"

    val Autopilot = "autopilot"

  val ArticleReview = "article-review"
  val InferFilter = "infer-filter"
  val ArticleExtend = "article-extend"
  val SourceEnrich = "source-enrich"
  val ProposeArticles = "propose-articles"
  val ArticleCompile = "article-compile"

  val AcquisitionSweep = "acquisition-sweep"
  val CurrencyCheck = "currency-check"

  val PlanGoalItem = "plan-goal"

  val OaIngest = "oa-ingest"

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def itemKinds(k: Kind): List[ManagerItem.Kind] = k match
    case Kind.Review =>
      List(ManagerItem.Kind(ArticleReview), ManagerItem.Kind(InferFilter), ManagerItem.Kind(ArticleExtend))
    case Kind.Enrich => List(ManagerItem.Kind(SourceEnrich))
    case Kind.Seed   =>
      List(ManagerItem.Kind(ProposeArticles), ManagerItem.Kind(ArticleCompile), ManagerItem.Kind(PlanGoalItem))
    case Kind.Watch =>
      List(ManagerItem.Kind(AcquisitionSweep), ManagerItem.Kind(CurrencyCheck), ManagerItem.Kind(OaIngest))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def itemKindOf(k: Kind, word: ItemKind.Word): Option[ManagerItem.Kind] =
    val w = (word).value.trim.toLowerCase
    val kinds = itemKinds(k)
    if w.isEmpty then (kinds.headOption).map(refactorValue => ManagerItem.Kind((refactorValue).value))
    else
      val alias = w match
        case "review" | "article"        => ArticleReview
        case "filter" | "infer"          => InferFilter
        case "extend"                    => ArticleExtend
        case "enrich" | "source"         => SourceEnrich
        case "propose" | "plan" | "seed" => ProposeArticles
        case "compile"                   => ArticleCompile
        case "sweep" | "acquisition"     => AcquisitionSweep
        case "currency"                  => CurrencyCheck
        case "plan-goal" | "goal-plan"   => PlanGoalItem
        case "ingest" | "backlog"        => OaIngest
        case other                       => other
      (Option(alias).filter(kinds.contains)).map(refactorValue2 => ManagerItem.Kind(refactorValue2))

  val ReviewId = "review"

  val EnrichId = "enrich"

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def seedId(goal: Goal.Ref): ManagerInfo.Id = ManagerInfo.Id(s"seed:${(goal).value.trim}")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def watchId(goal: Goal.Ref): ManagerInfo.Id = ManagerInfo.Id(s"watch:${(goal).value.trim}")

  val Actions: List[Actions] = List(ActionsValue("pause"), ActionsValue("resume"), ActionsValue("stop"))
