package com.histmersion
package web

import scala.scalajs.js
import com.histmersion.api.UiLabels
import io.github.iltotore.iron.constraint.any.Pure

/** Settings' sections, in the order of its side navigation; `rare`: under "Rarely needed". */
enum SettingsSection(
    val slug: SettingsSection.Slug,
    val label: SettingsSection.Label,
    val rare: SettingsSection.Rare = SettingsSection.Rare.Common
) derives CanEqual:
  case General extends SettingsSection(SettingsSection.Slug("general"), SettingsSection.Label("General"))
  case Data extends SettingsSection(SettingsSection.Slug("data"), SettingsSection.Label("Data and downloads"))
  case Finders
      extends SettingsSection(SettingsSection.Slug("sources"), SettingsSection.Label("Where to look for sources"))
  case Agents extends SettingsSection(SettingsSection.Slug("ai"), SettingsSection.Label("AI agents"))
  case Keys extends SettingsSection(SettingsSection.Slug("keys"), SettingsSection.Label("Keys and passwords"))
  case Calibre extends SettingsSection(SettingsSection.Slug("calibre"), SettingsSection.Label("Calibre and files"))
  case Backups extends SettingsSection(SettingsSection.Slug("backups"), SettingsSection.Label("Backups"))
  case Plugins extends SettingsSection(SettingsSection.Slug("plugins"), SettingsSection.Label("Plugins"))
  case People
      extends SettingsSection(
        SettingsSection.Slug("people"),
        SettingsSection.Label("People and access"),
        rare = SettingsSection.Rare.Rare
      )
  case Advanced
      extends SettingsSection(
        SettingsSection.Slug("advanced"),
        SettingsSection.Label("Advanced (all settings)"),
        rare = SettingsSection.Rare.Rare
      )
  case Credits
      extends SettingsSection(
        SettingsSection.Slug("credits"),
        SettingsSection.Label("Credits and licences"),
        rare = SettingsSection.Rare.Rare
      )
  case About
      extends SettingsSection(
        SettingsSection.Slug("about"),
        SettingsSection.Label("About Histmersion"),
        rare = SettingsSection.Rare.Rare
      )

object SettingsSection:
  export api.research.Article.Slug
  export UiLabels.Text as Label
  enum Rare derives CanEqual:
    case Rare, Common
  object Rare:
    extension (rare: Rare) def rare: Boolean = rare == Rare
