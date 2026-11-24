package com.histmersion
package web.settings

import com.histmersion.api.instance.UiConfig
import com.histmersion.web.{ResearchSection, Route, RouteLink}
import com.raquo.laminar.api.L.*
import com.histmersion.web.SettingsSection
import com.histmersion.api.Wording
import com.histmersion.lint.{LintRule, tolerated}

/** Settings › AI agents: whether this server runs research agents, how many at once, and the AI providers' addresses
  * and time limits. Which model does which task is Research › AI models and limits; the providers' keys are Keys and
  * passwords.
  */
object AgentsSection:

  private val Providers = List("anthropic", "openai", "local")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def pane(ui: UiConfig): HtmlElement =
    div(
      cls := "settings-section",
      p(
        cls := "settings-lead",
        "Research agents are optional: Histmersion works without them. Which model does which " +
          "task is chosen in ",
        RouteLink(Route.Research(ResearchSection.Models), "Research › AI models and limits"),
        "; the providers' keys are in ",
        RouteLink(Route.Settings(SettingsSection.Keys), "Keys and passwords"),
        "."
      ),
      SettingsForm(
        SettingsForm.ApplyTitle("Research agents"),
        None,
        List(
          SettingsForm.Item(
            SettingsForm.Item.Key("agents.enabled"),
            Some(SettingsForm.Item.Name("Run research agents on this server"))
          ),
          SettingsForm.Item(
            SettingsForm.Item.Key("agents.max-concurrent-runs"),
            Some(SettingsForm.Item.Name("Agent runs at once"))
          ),
          SettingsForm.Item(
            SettingsForm.Item.Key("agents.budgets.run-tokens"),
            Some(SettingsForm.Item.Name("Tokens one run may use"))
          ),
          SettingsForm.Item(
            SettingsForm.Item.Key("agents.budgets.daily-usd"),
            Some(SettingsForm.Item.Name("Money all runs together may spend a day (USD)"))
          )
        )
      ),
      SettingsForm(
        SettingsForm.ApplyTitle("AI providers"),
        Some(SettingsForm.Intro("Where each provider is reached, and how long one answer may take.")),
        Providers.flatMap(p =>
          List(
            SettingsForm
              .Item(
                SettingsForm.Item.Key(s"agents.providers.$p.base-url"),
                Some(SettingsForm.Item.Name(s"${Wording.humanize(Wording.Id(p))}: address"))
              ),
            SettingsForm.Item(
              SettingsForm.Item.Key(s"agents.providers.$p.timeout-seconds"),
              Some(SettingsForm.Item.Name(s"${Wording.humanize(Wording.Id(p))}: time limit (seconds)"))
            )
          )
        ) ++
          List(
            SettingsForm.Item(
              SettingsForm.Item.Key("agents.providers.claude-code.command"),
              Some(SettingsForm.Item.Name("Claude Code: the claude command"))
            ),
            SettingsForm.Item(
              SettingsForm.Item.Key("agents.providers.codex.command"),
              Some(SettingsForm.Item.Name("Codex: the codex command"))
            )
          )
      )
    )
