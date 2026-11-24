package com.histmersion
package web

import com.histmersion.api.automation.Managers.*
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.lint.{LintRule, tolerated}

/** The settings line a manager window shows (the server's own wording). */
object ManagersHandlersText:
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def describe(c: ManagerConfig): String =
    List(
      c.maxRuns.map(n => s"$n at once"),
      c.rounds.map(n => s"$n rounds"),
      c.maxRunsPerArticle.map(n => s"≤ $n runs per article"),
      c.noProgressRuns.map(n => s"no-progress window $n"),
      c.reopenLimit.map(n => s"reopen limit $n"),
      c.cooldownMinutes.map(n => s"cooldown $n min"),
      c.includeTags.filter(_.nonEmpty).map(t => s"tags ${t.mkString(", ")}"),
      c.includeGoal.map(g => s"goal $g"),
      c.models.filter(_.nonEmpty).map(ms => ms.map(_.text).mkString("; ")),
      c.budgetUsd.map(u => s"$u USD per run"),
      c.dailyUsd.filter(_.value > 0).map(u => s"$u USD a day")
    ).flatten.mkString(" · ")
