package com.histmersion
package server.agents

import com.histmersion.api.automation.PlanQuota.*

import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.TwoCases

/** What one refresh did: how (`probe`, `app-server`), whether a reading came back, and what. */
@movesTo(FeaturePlace.AutomationAgents)
final case class Refreshed(mode: Refreshed.Mode, ok: Refreshed.Ok, detail: Refreshed.Detail):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def text: Refreshed.Text = Refreshed.Text(s"$mode: $detail")

object Refreshed:
  type Text = Text.T
  object Text extends Newtype[String, Pure]

  type Mode = Mode.T
  object Mode extends Newtype[String, Pure]
  enum Ok derives CanEqual:
    case Ok, Failed
  object Ok extends TwoCases[Ok]:
    extension (ok: Ok) def ok: Boolean = ok == Ok
  type Detail = Detail.T
  object Detail extends Newtype[String, Pure]
