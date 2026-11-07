package com.histmersion.web.settings

import com.histmersion.api.instance.UiConfig
import com.histmersion.web.{ResearchSection, Route, RouteLink}
import com.raquo.laminar.api.L.*

/** Settings › AI agents: whether this server runs research agents, how many at once, and the AI providers' addresses
  * and time limits. Which model does which task is Research › AI models and limits; the providers' keys are Keys and
  * passwords.
  */
object AgentsSection:

  private val Providers = List("anthropic", "openai", "local")

  def pane(ui: UiConfig): HtmlElement =
    div(
      cls := "settings-section",
      p(
        cls := "settings-lead",
        "Research agents are optional: Histmersion works without them. Which model does which " +
          "task is chosen in ",
        RouteLink(Route.Research(ResearchSection.Models), "Research › AI models and limits"),
        "; the providers' keys are in ",
        RouteLink(Route.Settings(com.histmersion.web.SettingsSection.Keys), "Keys and passwords"),
        "."
      ),
      SettingsForm(
        com.histmersion.web.settings.SettingsForm.ApplyTitle("Research agents"),
        None,
        List(
          SettingsForm.Item(com.histmersion.web.settings.SettingsForm.Item.Key("agents.enabled"), Some(com.histmersion.web.settings.SettingsForm.Item.Name("Run research agents on this server"))),
          SettingsForm.Item(com.histmersion.web.settings.SettingsForm.Item.Key("agents.max-concurrent-runs"), Some(com.histmersion.web.settings.SettingsForm.Item.Name("Agent runs at once"))),
          SettingsForm.Item(com.histmersion.web.settings.SettingsForm.Item.Key("agents.budgets.run-tokens"), Some(com.histmersion.web.settings.SettingsForm.Item.Name("Tokens one run may use"))),
          SettingsForm.Item(com.histmersion.web.settings.SettingsForm.Item.Key("agents.budgets.daily-usd"), Some(com.histmersion.web.settings.SettingsForm.Item.Name("Money all runs together may spend a day (USD)")))
        )
      ),
      SettingsForm(
        com.histmersion.web.settings.SettingsForm.ApplyTitle("AI providers"),
        Some(com.histmersion.web.settings.SettingsForm.Intro("Where each provider is reached, and how long one answer may take.")),
        Providers.flatMap(p =>
          List(
            SettingsForm
              .Item(
                SettingsForm.Item.Key(s"agents.providers.$p.base-url"),
                Some(SettingsForm.Item.Name(s"${com.histmersion.api.Wording.humanize(com.histmersion.api.Wording.Id(p))}: address"))
              ),
            SettingsForm.Item(
              SettingsForm.Item.Key(s"agents.providers.$p.timeout-seconds"),
              Some(SettingsForm.Item.Name(s"${com.histmersion.api.Wording.humanize(com.histmersion.api.Wording.Id(p))}: time limit (seconds)"))
            )
          )
        ) ++
          List(
            SettingsForm.Item(com.histmersion.web.settings.SettingsForm.Item.Key("agents.providers.claude-code.command"), Some(com.histmersion.web.settings.SettingsForm.Item.Name("Claude Code: the claude command"))),
            SettingsForm.Item(com.histmersion.web.settings.SettingsForm.Item.Key("agents.providers.codex.command"), Some(com.histmersion.web.settings.SettingsForm.Item.Name("Codex: the codex command")))
          )
      )
    )
