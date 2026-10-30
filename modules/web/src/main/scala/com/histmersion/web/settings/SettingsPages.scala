package com.histmersion.web.settings

import com.histmersion.api.UiConfig
import com.histmersion.web.{AboutView, Auth, CreditsView, Route, SettingsSection}
import com.raquo.laminar.api.L.*

/** Settings' pages: one per section of its side navigation, each over the settings file as the server reads it now
  * (read when a page opens).
  */
object SettingsPages:

  /** What a section's page says under its title. Pure. */
  def lead(s: SettingsSection): Option[String] = s match
    case SettingsSection.General => Some("How Histmersion looks and where the map opens.")
    case SettingsSection.Finders => Some("Where Histmersion looks for the free, legal copy of a paper you add.")
    case SettingsSection.Agents  => Some("Research agents that read, enrich and write for you — optional.")
    case SettingsSection.Keys    =>
      Some("Keys and passwords: whether each is set, and where it is kept. Never the key itself.")
    case SettingsSection.Calibre => Some("The Calibre library that keeps your originals, and the server's folders.")
    case SettingsSection.Backups => Some("Copies of the database, kept on this computer.")
    case SettingsSection.Plugins => Some("What the plugins add, each on or off.")
    case _                       => None

  def page(ui: UiConfig, s: SettingsSection): HtmlElement =
    div(
      cls := "settings-page",
      dataAttr("section") := s.slug,
      onMountCallback { _ =>
        SettingsStore.clearResult()
        SettingsStore.load()
      },
      lead(s).map(t => p(cls := "settings-page-lead", t)),
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
