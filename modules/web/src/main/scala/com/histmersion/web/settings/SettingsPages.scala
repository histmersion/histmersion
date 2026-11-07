package com.histmersion.web.settings

import com.histmersion.api.instance.UiConfig
import com.histmersion.web.{AboutView, Auth, CreditsView, Route, SettingsSection}
import com.raquo.laminar.api.L.*

/** Settings' pages: one per section of its side navigation, each over the settings file as the server reads it now
  * (read when a page opens).
  */
object SettingsPages:
  type Lead = Lead.T
  object Lead extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** What a section's page says under its title. Pure. */

  def lead(s: SettingsSection): Option[com.histmersion.web.settings.SettingsPages.Lead] = s match
    case SettingsSection.General => Some(com.histmersion.web.settings.SettingsPages.Lead("How Histmersion looks and where the map opens."))
    case SettingsSection.Finders => Some(com.histmersion.web.settings.SettingsPages.Lead("Where Histmersion looks for the free, legal copy of a paper you add."))
    case SettingsSection.Agents  => Some(com.histmersion.web.settings.SettingsPages.Lead("Research agents that read, enrich and write for you — optional."))
    case SettingsSection.Keys    =>
      Some(com.histmersion.web.settings.SettingsPages.Lead("Keys and passwords: whether each is set, and where it is kept. Never the key itself."))
    case SettingsSection.Calibre => Some(com.histmersion.web.settings.SettingsPages.Lead("The Calibre library that keeps your originals, and the server's folders."))
    case SettingsSection.Backups => Some(com.histmersion.web.settings.SettingsPages.Lead("Copies of the database, kept on this computer."))
    case SettingsSection.Plugins => Some(com.histmersion.web.settings.SettingsPages.Lead("What the plugins add, each on or off."))
    case _                       => None

  def page(ui: UiConfig, s: SettingsSection): HtmlElement =
    div(
      cls := "settings-page",
      dataAttr("section") := s.slug.value,
      onMountCallback { _ =>
        SettingsStore.clearResult()
        SettingsStore.load()
      },
      lead(s).map(t => p(cls := "settings-page-lead", t.value)),
      s match
        case SettingsSection.General  => GeneralSection.pane(ui)
        case SettingsSection.Data     => DataSection.pane(ui)
        case SettingsSection.Finders  => FindersSection.pane(ui)
        case SettingsSection.Agents   => AgentsSection.pane(ui)
        case SettingsSection.Keys     => KeysSection.pane
        case SettingsSection.Calibre  => CalibreSection.pane
        case SettingsSection.Backups  => BackupsSection.pane
        case SettingsSection.Plugins  => PluginsSection.pane(ui)
        case SettingsSection.People   => PeopleSection.pane
        case SettingsSection.Advanced => AdvancedSection.pane
        case SettingsSection.Credits  => CreditsView.pane
        case SettingsSection.About    => AboutView.pane
    )
