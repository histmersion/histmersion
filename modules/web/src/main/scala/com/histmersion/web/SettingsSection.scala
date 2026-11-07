package com.histmersion.web

import scala.scalajs.js

/** Settings' sections, in the order of its side navigation; `rare`: under "Rarely needed". */
enum SettingsSection(val slug: SettingsSection.Slug, val label: SettingsSection.Label, val rare: SettingsSection.Rare = com.histmersion.web.SettingsSection.Rare(false)):
  case General extends SettingsSection(com.histmersion.web.SettingsSection.Slug("general"), com.histmersion.web.SettingsSection.Label("General"))
  case Data extends SettingsSection(com.histmersion.web.SettingsSection.Slug("data"), com.histmersion.web.SettingsSection.Label("Data and downloads"))
  case Finders extends SettingsSection(com.histmersion.web.SettingsSection.Slug("sources"), com.histmersion.web.SettingsSection.Label("Where to look for sources"))
  case Agents extends SettingsSection(com.histmersion.web.SettingsSection.Slug("ai"), com.histmersion.web.SettingsSection.Label("AI agents"))
  case Keys extends SettingsSection(com.histmersion.web.SettingsSection.Slug("keys"), com.histmersion.web.SettingsSection.Label("Keys and passwords"))
  case Calibre extends SettingsSection(com.histmersion.web.SettingsSection.Slug("calibre"), com.histmersion.web.SettingsSection.Label("Calibre and files"))
  case Backups extends SettingsSection(com.histmersion.web.SettingsSection.Slug("backups"), com.histmersion.web.SettingsSection.Label("Backups"))
  case Plugins extends SettingsSection(com.histmersion.web.SettingsSection.Slug("plugins"), com.histmersion.web.SettingsSection.Label("Plugins"))
  case People extends SettingsSection(com.histmersion.web.SettingsSection.Slug("people"), com.histmersion.web.SettingsSection.Label("People and access"), rare = com.histmersion.web.SettingsSection.Rare(true))
  case Advanced extends SettingsSection(com.histmersion.web.SettingsSection.Slug("advanced"), com.histmersion.web.SettingsSection.Label("Advanced (all settings)"), rare = com.histmersion.web.SettingsSection.Rare(true))
  case Credits extends SettingsSection(com.histmersion.web.SettingsSection.Slug("credits"), com.histmersion.web.SettingsSection.Label("Credits and licences"), rare = com.histmersion.web.SettingsSection.Rare(true))
  case About extends SettingsSection(com.histmersion.web.SettingsSection.Slug("about"), com.histmersion.web.SettingsSection.Label("About Histmersion"), rare = com.histmersion.web.SettingsSection.Rare(true))

object SettingsSection:
  type Slug = com.histmersion.web.Preview.Item.Slug
  val Slug: _root_.com.histmersion.web.Preview.Item.Slug.type = _root_.com.histmersion.web.Preview.Item.Slug
  type Label = com.histmersion.api.UiLabels.Text
  val Label: _root_.com.histmersion.api.UiLabels.Text.type = _root_.com.histmersion.api.UiLabels.Text
  type Rare = Rare.T
  object Rare extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
