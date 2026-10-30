package com.histmersion.web.settings

import com.histmersion.api.UiConfig
import com.histmersion.web.{ResearchSection, Route, RouteLink}
import com.raquo.laminar.api.L.*

/** Settings › AI agents: whether this server runs research agents, how many at once, and the AI providers'
  * addresses and time limits. Which model does which task is Research › AI models and limits; the providers'
  * keys are Keys and passwords. */
object AgentsSection:

  private val Providers = List("anthropic", "openai", "local")

  def pane(ui: UiConfig): HtmlElement =
    div(cls := "settings-section",
      p(cls := "settings-lead", "Research agents are optional: Histmersion works without them. Which model does which " +
        "task is chosen in ", RouteLink(Route.Research(ResearchSection.Models), "Research › AI models and limits"),
        "; the providers' keys are in ", RouteLink(Route.Settings(com.histmersion.web.SettingsSection.Keys), "Keys and passwords"), "."),
      SettingsForm("Research agents", None, List(
        SettingsForm.Item("agents.enabled", Some("Run research agents on this server")),
        SettingsForm.Item("agents.max-concurrent-runs", Some("Agent runs at once")),
        SettingsForm.Item("agents.budgets.run-tokens", Some("Tokens one run may use")),
        SettingsForm.Item("agents.budgets.daily-usd", Some("Money all runs together may spend a day (USD)")))),
      SettingsForm("AI providers", Some("Where each provider is reached, and how long one answer may take."),
        Providers.flatMap(p => List(
          SettingsForm.Item(s"agents.providers.$p.base-url", Some(s"${com.histmersion.api.Wording.humanize(p)}: address")),
          SettingsForm.Item(s"agents.providers.$p.timeout-seconds", Some(s"${com.histmersion.api.Wording.humanize(p)}: time limit (seconds)")))) ++
          List(SettingsForm.Item("agents.providers.claude-code.command", Some("Claude Code: the claude command")),
            SettingsForm.Item("agents.providers.codex.command", Some("Codex: the codex command")))))
