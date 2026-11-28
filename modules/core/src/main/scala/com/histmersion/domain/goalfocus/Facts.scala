package com.histmersion
package domain.goalfocus

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.automation.Goal
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.goalfocus.Work
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.AutopilotPolicy
import com.histmersion.Newtype
import com.histmersion.domain.autopilotpolicy.NextFacts
import com.histmersion.api.Plural
import com.histmersion.domain.GoalFocus.finished
import com.histmersion.domain.goalfocus.Stage
import com.histmersion.domain.goalfocus.Settings
import com.histmersion.domain.GoalFocus.Rationale
import com.histmersion.api.automation.Managers.*
import com.histmersion.domain.automation.ItemStatus
import com.histmersion.domain.goalfocus.Fit
import com.histmersion.domain.GoalFocus.Code

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
final case class Facts(
    slug: Goal.Slug,
    parent: Option[Goal.Slug] = None,
    status: Goal.Status.Wire = Goal.Status.Wire(Goal.Status.Seeding),
    createdAt: Facts.CreatedAt = Facts.CreatedAt(""),
    sources: Facts.Sources = Facts.Sources(0),
    articles: Facts.Articles = Facts.Articles(0),
    unreviewed: Facts.Unreviewed = Facts.Unreviewed(0),
    compileOpen: Facts.CompileOpen = Facts.CompileOpen(0),
    work: List[Work] = Nil,
    lastStop: Option[Facts.LastStop] = None,
    pinned: Facts.Pinned = Facts.Pinned(false),
    priority: Facts.Priority = Facts.Priority(0),
    active: Facts.Active = Facts.Active(false),
    since: Option[Facts.Since] = None
)

object Facts:
  type CreatedAt = CreatedAt.T
  object CreatedAt extends Newtype[String, Pure]
  type Sources = Sources.T
  object Sources extends Newtype[Int, Pure]
  export NextFacts.Articles
  export NextFacts.Unreviewed
  export NextFacts.CompileOpen
  type LastStop = LastStop.T
  object LastStop extends Newtype[String, Pure]
  type Pinned = Pinned.T
  object Pinned extends Newtype[Boolean, Pure]
  type Priority = Priority.T
  object Priority extends Newtype[Int, Pure]
  type Active = Active.T
  object Active extends Newtype[Boolean, Pure]
  type Since = Since.T
  object Since extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def counted(n: Int, singular: String): String =
    Plural.count(Plural.CountN(n.toLong), Plural.CountSingular(singular)).value

  private def unfinishedKids(f: Facts, kids: Map[Goal.Slug, List[Facts]]): List[Facts] =
    kids.getOrElse(f.slug, Nil).filterNot(k => finished(k.status))

  extension (f: Facts)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def stage(minSources: Settings.MinSources): Stage =
      if f.unreviewed.value > 0 then Stage.Reviewing
      else if f.compileOpen.value > 0 then Stage.Compiling
      else if f.sources.value >= math.max(1, minSources.value) then Stage.Ready
      else Stage.Gathering

  extension (f: Facts)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def stageWhy(st: Stage, minSources: Settings.MinSources): Rationale = st match
      case Stage.Reviewing =>
        Rationale(s"${f.unreviewed} of ${counted(f.articles.value, "article")} still to pass review")
      case Stage.Compiling =>
        Rationale(s"${counted(f.compileOpen.value, "approved article")} to compile")
      case Stage.Ready =>
        Rationale(
          s"${counted(f.sources.value, "source")} (at least ${math.max(1, minSources.value)}), no article yet"
        )
      case Stage.Gathering =>
        Rationale(s"${counted(f.sources.value, "source")}: still gathering its plan and sources")

  extension (f: Facts)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fit(kids: Map[Goal.Slug, List[Facts]], s: Settings): Fit =
      if finished(f.status) then Fit.Finished
      else
        val open = unfinishedKids(f, kids)
        val allowed = if open.isEmpty then f.work else f.work.filter(_.kind.value == PlanGoalItem)
        // an item that waits for another (a proposal for its plan, a review for its filter) can run only when
        // that one can: a proposal behind a plan waiting for a person waits for that person too
        val runnable = allowed.filter(w => w.runnable && w.blockers(f.work).forall(_.runnable))
        if runnable.isEmpty && open.nonEmpty then
          Fit.Waits(
            Code.Children,
            Rationale(s"waits for its ${counted(open.size, "unfinished sub-goal")} before its own articles")
          )
        else if f.work.isEmpty then
          f.lastStop match
            case Some(o) =>
              Fit.Waits(
                Code.Stuck,
                Rationale(s"stuck: its last item stopped — ${o.value.take(300)}")
              )
            case None =>
              Fit.Waits(
                Code.Idle,
                Rationale("idle: nothing for a model to do until its cron fires or new sources arrive")
              )
        else if runnable.isEmpty then
          val ids = (allowed ++ allowed
            .flatMap(_.blockers(f.work))).flatMap(w => w.approval.filter(_ => w.approvalByPerson.value)).distinct.sorted
          if ids.nonEmpty then
            Fit.Waits(
              Code.Person,
              Rationale(s"waits for a person: ${ids.map(i => s"approval #$i").mkString(", ")}")
            )
          else Fit.Waits(Code.Held, Rationale("its items are held by a person"))
        else if !f.active.value && !f.pinned.value && s.plansByPerson.value && s.max.value > 0 && s.openPersonPlans.value >= s.max.value &&
          runnable.forall(w => w.kind.value == PlanGoalItem && w.status == ItemStatus.Pending.wire)
        then
          Fit.Waits(
            Code.Plans,
            Rationale(
              s"its next step is a goal plan, and ${counted(s.openPersonPlans.value, "goal plan")} already wait for a person"
            )
          )
        else Fit.Candidate(f.stage(s.minSources))
