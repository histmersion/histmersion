package com.histmersion
package web.settings

import com.histmersion.api.instance.UiConfig
import com.histmersion.web.{AboutView, Auth, CreditsView, Route, SettingsSection}
import com.raquo.laminar.api.L.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.web.{:=, given}
import com.histmersion.lint.{tolerated, LintRule}

/** Settings' pages: one per section of its side navigation, each over the settings file as the server reads it now
  * (read when a page opens).
  */
object SettingsPages:
  type Lead = Lead.T
  object Lead extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)

  /** What a section's page says under its title. Pure. */

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def lead(s: SettingsSection): Option[Lead] = s match
    case SettingsSection.General => Some(Lead("How Histmersion looks and where the map opens."))
    case SettingsSection.Finders => Some(Lead("Where Histmersion looks for the free, legal copy of a paper you add."))
    case SettingsSection.Agents  => Some(Lead("Research agents that read, enrich and write for you — optional."))
    case SettingsSection.Keys    =>
      Some(Lead("Keys and passwords: whether each is set, and where it is kept. Never the key itself."))
    case SettingsSection.Calibre =>
      Some(Lead("The Calibre library that keeps your originals, and the server's folders."))
    case SettingsSection.Backups => Some(Lead("Copies of the database, kept on this computer."))
    case SettingsSection.Plugins => Some(Lead("What the plugins add, each on or off."))
    case _                       => None

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def page(ui: UiConfig, s: SettingsSection): HtmlElement =
    div(
      cls := "settings-page",
      dataAttr("section") := s.slug.value,
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
