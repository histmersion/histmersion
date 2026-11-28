package com.histmersion
package domain.automation.autopilot

import com.histmersion.domain.automation.Goal
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.AutopilotPolicy.Rationale
import com.histmersion.domain.AutopilotPolicy.counted

final case class GoalFacts(
    slug: GoalFacts.Slug,
    status: Goal.Status.Wire,
    managed: GoalFacts.Managed,
    articles: GoalFacts.Articles,
    compileOpen: GoalFacts.CompileOpen,
    reviewed: GoalFacts.Reviewed,
    children: List[Goal.Status.Wire]
)

object GoalFacts:
  type Slug = Slug.T
  object Slug extends Newtype[String, Pure]
  type Managed = Managed.T
  object Managed extends Newtype[Boolean, Pure]
  type Articles = Articles.T
  object Articles extends Newtype[Int, Pure]
  type CompileOpen = CompileOpen.T
  object CompileOpen extends Newtype[Boolean, Pure]
  type Reviewed = Reviewed.T
  object Reviewed extends Newtype[Boolean, Pure]

  private def rank(s: Goal.Status.Wire): Int = Goal.Status.all.indexOf(s)

  extension (f: GoalFacts)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def advance: Option[(Goal.Status.Wire, Rationale)] =
      val cur = rank(f.status)
      if cur < 0 || f.status.value == Goal.Status.Done then None
      else
        val kids = f.children.map(rank)
        val hasOwn = f.articles.value > 0
        val compiled = (hasOwn || kids.nonEmpty) && !f.compileOpen.value && kids.forall(
          _ >= rank(Goal.Status.Wire(Goal.Status.Compiled))
        )
        val reviewed =
          compiled && (!hasOwn || f.reviewed.value) && kids.forall(_ >= rank(Goal.Status.Wire(Goal.Status.Reviewed)))
        val (target, why) =
          if reviewed then
            (
              Goal.Status.Wire(Goal.Status.Reviewed),
              (if hasOwn then s"its ${counted(f.articles.value, "article")} passed review" else "") +
                (if kids.nonEmpty then
                   s"${if hasOwn then ", " else ""}its ${counted(kids.size, "sub-goal")} are reviewed"
                 else "")
            )
          else if compiled then
            (
              Goal.Status.Wire(Goal.Status.Compiled),
              (if hasOwn then s"${counted(f.articles.value, "article")} compiled, none left to compile"
               else "nothing left to compile") +
                (if kids.nonEmpty then s", its ${counted(kids.size, "sub-goal")} are compiled" else "")
            )
          else if f.managed.value then
            (Goal.Status.Wire(Goal.Status.Seeding), "its seeding manager and watcher are active")
          else (f.status, "")
        Option.when(rank(target) > cur)((target, Rationale(why)))
