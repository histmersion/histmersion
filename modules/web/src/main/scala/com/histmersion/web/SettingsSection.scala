package com.histmersion.web

import scala.scalajs.js

/** Settings' sections, in the order of its side navigation; `rare`: under "Rarely needed". */
enum SettingsSection(val slug: SettingsSection.Slug, val label: SettingsSection.Label, val rare: SettingsSection.Rare = false):
  case General extends SettingsSection("general", "General")
  case Data extends SettingsSection("data", "Data and downloads")
  case Finders extends SettingsSection("sources", "Where to look for sources")
  case Agents extends SettingsSection("ai", "AI agents")
  case Keys extends SettingsSection("keys", "Keys and passwords")
  case Calibre extends SettingsSection("calibre", "Calibre and files")
  case Backups extends SettingsSection("backups", "Backups")
  case Plugins extends SettingsSection("plugins", "Plugins")
  case People extends SettingsSection("people", "People and access", rare = true)
  case Advanced extends SettingsSection("advanced", "Advanced (all settings)", rare = true)
  case Credits extends SettingsSection("credits", "Credits and licences", rare = true)
  case About extends SettingsSection("about", "About Histmersion", rare = true)

object SettingsSection:
  type Slug = com.histmersion.web.Preview.Item.Slug
  val Slug: _root_.com.histmersion.web.Preview.Item.Slug.type = _root_.com.histmersion.web.Preview.Item.Slug
  type Label = com.histmersion.api.UiLabels.Text
  val Label: _root_.com.histmersion.api.UiLabels.Text.type = _root_.com.histmersion.api.UiLabels.Text
  type Rare = Rare.T
  object Rare extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
