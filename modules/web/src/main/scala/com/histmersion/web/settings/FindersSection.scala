package com.histmersion.web.settings

import com.histmersion.api.Wording
import com.histmersion.api.instance.UiConfig
import com.raquo.laminar.api.L.*

/** Settings › Where to look for sources: the open-access finders the acquisition asks, in order, for a paper's free
  * copy — each on or off — and the contact e-mail the scholarly indexes ask for.
  */
object FindersSection:

  /** The finders among the plugins: those that register a source fetcher. Pure. */
  def finders(ui: UiConfig): List[com.histmersion.api.plugins.PluginStatus] =
    ui.plugins.plugins.filter(p => p.provides.exists(_.value.startsWith("source-fetcher:")) || FinderIds(p.id.value))

  /** The built-in finders, also when they are off (an off plugin registers nothing). */
  val FinderIds: Set[String] =
    Set("archive-org", "oa-unpaywall", "oa-core", "oa-openalex", "oa-landing", "pmc", "arxiv")

  def pane(ui: UiConfig): HtmlElement =
    div(
      cls := "settings-section",
      SettingsForm(
        com.histmersion.web.settings.SettingsForm.ApplyTitle("Your contact e-mail"),
        Some(
          SettingsForm.Intro(
            "Crossref, OpenAlex and Unpaywall ask the people who use them for an e-mail address, so that they can " +
              "write if something goes wrong. Histmersion sends it to those three services only — as the address they " +
              "ask for, in their own request — and to nothing else. Without it Unpaywall is not asked, and the other two " +
              "answer more slowly."
          )
        ),
        List(
          SettingsForm.Item(com.histmersion.web.settings.SettingsForm.Item.Key("acquire.contact-email"), Some(com.histmersion.web.settings.SettingsForm.Item.Name("E-mail address")), Some(com.histmersion.web.settings.SettingsForm.Item.Help("Leave it empty to stay anonymous.")))
        )
      ),
      SettingsForm(
        SettingsForm.ApplyTitle("Where to look for a paper's free copy"),
        Some(
          SettingsForm.Intro(
            "When you add a paper by its DOI, these places are asked in turn for an open-access copy. Turn off any " +
              "you do not want asked."
          )
        ),
        finders(ui).map(p =>
          SettingsForm.Item(
            SettingsForm.Item.Key(s"plugins.${p.id}.enabled"),
            Some(SettingsForm.Item.Name(Wording.plugin(Wording.PluginId(p.id.value)))),
            None,
            fallback = Some(SettingsForm.Item.Fallback(p.enabled.toString))
          )
        )
      )
    )
