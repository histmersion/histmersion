package com.histmersion
package domain.schedulerpolicy

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.automation.Managers.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.schedulerpolicy.Candidate
import com.histmersion.Newtype
import com.histmersion.domain.schedulerpolicy.Settings

enum JobType(val wire: JobType.Wire, val label: JobType.Label) derives CanEqual:
  case GoalPlan extends JobType(JobType.Wire("goal-plan"), JobType.Label("goal plans"))
  case Propose extends JobType(JobType.Wire("propose"), JobType.Label("article proposals"))
  case Sweep extends JobType(JobType.Wire("sweep"), JobType.Label("candidate sweeps and their ranking"))
  case Currency extends JobType(JobType.Wire("currency"), JobType.Label("currency checks"))
  case Enrich extends JobType(JobType.Wire("enrich"), JobType.Label("source enrichment"))
  case Compile extends JobType(JobType.Wire("compile"), JobType.Label("article compiles"))
  case Extend extends JobType(JobType.Wire("extend"), JobType.Label("article extensions"))
  case Filter extends JobType(JobType.Wire("filter"), JobType.Label("coverage filters"))
  case Review extends JobType(JobType.Wire("review"), JobType.Label("reviews and their revisions"))

object JobType:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[JobType] = values.find(_.wire.value == s.value.trim.toLowerCase(java.util.Locale.ROOT))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(itemKind: Candidate.Kind): JobType = itemKind.value match
    case PlanGoalItem     => GoalPlan
    case ProposeArticles  => Propose
    case AcquisitionSweep => Sweep
    case CurrencyCheck    => Currency
    case SourceEnrich     => Enrich
    case ArticleCompile   => Compile
    case ArticleExtend    => Extend
    case InferFilter      => Filter
    case _                => Review

  extension (t: JobType)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def shareDue(started: List[JobType], minShare: Settings.MinShare): Boolean =
      minShare.value > 0 && {
        val n = math.ceil(1.0 / math.min(1.0, minShare.value)).toInt
        started.size >= n && !started.take(n).contains(t)
      }
