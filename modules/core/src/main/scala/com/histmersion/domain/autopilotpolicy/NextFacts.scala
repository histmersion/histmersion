package com.histmersion
package domain.autopilotpolicy

import java.time.Instant
import com.histmersion.domain.automation.ItemStatus
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.automation.Manager
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.AutopilotPolicy.Description
import com.histmersion.domain.AutopilotPolicy.CooldownHours
import com.histmersion.domain.AutopilotPolicy.counted

final case class NextFacts(
    goal: NextFacts.Goal,
    status: Goal.Status.Wire,
    articles: NextFacts.Articles,
    unreviewed: NextFacts.Unreviewed,
    compileOpen: NextFacts.CompileOpen,
    seedState: Option[Manager.State.Wire],
    proposeStatus: Option[ItemStatus.Wire],
    proposeSettled: Option[java.time.Instant]
)

object NextFacts:
  type Goal = Goal.T
  object Goal extends Newtype[String, Pure]
  type Articles = Articles.T
  object Articles extends Newtype[Int, Pure]
  type Unreviewed = Unreviewed.T
  object Unreviewed extends Newtype[Int, Pure]
  type CompileOpen = CompileOpen.T
  object CompileOpen extends Newtype[Int, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def articlesInProgress(unreviewed: NextFacts.Unreviewed, compileOpen: NextFacts.CompileOpen): Option[Description] =
    Option.when(unreviewed.value > 0 || compileOpen.value > 0)(
      Description(
        "its articles in progress come first (" + List(
          Option.when(compileOpen.value > 0)(s"$compileOpen to compile"),
          Option.when(unreviewed.value > 0)(s"$unreviewed to pass review")
        ).flatten.mkString(", ") +
          "): a new proposal waits until they are published and reviewed"
      )
    )

  extension (f: NextFacts)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def nextArticle(now: java.time.Instant, cooldownHours: CooldownHours): Option[Description] =
      val open = f.proposeStatus.exists(st => ItemStatus.parse(st).exists(!_.isFinal))
      val cooled =
        f.proposeSettled.forall(t => !t.plusSeconds(math.max(0, cooldownHours.value).toLong * 3600L).isAfter(now))
      Option.when(
        cooldownHours.value > 0 && f.status.value != domain.automation.Goal.Status.Done && f.articles.value > 0 &&
          articlesInProgress(f.unreviewed, f.compileOpen).isEmpty && f.seedState.contains(
            Manager.State.Active.wire
          ) && !open && cooled
      )(
        Description(
          s"its ${counted(f.articles.value, "article")} are compiled and passed review: its next article is proposed " +
            "(the plan goes through the article-plan approvals)"
        )
      )
