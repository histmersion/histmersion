package com.histmersion.web.settings

import com.histmersion.api.Wording
import com.histmersion.api.instance.UiConfig
import com.raquo.laminar.api.L.*

/** Settings › Where to look for sources: the open-access finders the acquisition asks, in order, for a paper's free
  * copy — each on or off — and the contact e-mail the scholarly indexes ask for.
  */
object FindersSection:

  /** The finders among the plugins: those that register a source fetcher. Pure. */
  type FinderIds = FinderIdsValue
  type FinderIdsValue = FinderIdsValue.T
  object FinderIdsValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def finders(ui: UiConfig): List[com.histmersion.api.plugins.PluginStatus] =
    ui.plugins.plugins.filter(p => p.provides.exists(_.startsWith("source-fetcher:")) || FinderIds(p.id))

  /** The built-in finders, also when they are off (an off plugin registers nothing). */
  val FinderIds: Set[com.histmersion.web.settings.FindersSection.FinderIds] =
    Set("archive-org", "oa-unpaywall", "oa-core", "oa-openalex", "oa-landing", "pmc", "arxiv")

  def pane(ui: UiConfig): HtmlElement =
    div(
      cls := "settings-section",
      SettingsForm(
        "Your contact e-mail",
        Some(
          "Crossref, OpenAlex and Unpaywall ask the people who use them for an e-mail address, so that they can " +
            "write if something goes wrong. Histmersion sends it to those three services only — as the address they " +
            "ask for, in their own request — and to nothing else. Without it Unpaywall is not asked, and the other two " +
            "answer more slowly."
        ),
        List(
          SettingsForm.Item("acquire.contact-email", Some("E-mail address"), Some("Leave it empty to stay anonymous."))
        )
      ),
      SettingsForm(
        "Where to look for a paper's free copy",
        Some(
          "When you add a paper by its DOI, these places are asked in turn for an open-access copy. Turn off any " +
            "you do not want asked."
        ),
        finders(ui).map(p =>
          SettingsForm.Item(
            s"plugins.${p.id}.enabled",
            Some(Wording.plugin(p.id)),
            None,
            fallback = Some(p.enabled.toString)
          )
        )
      )
    )
