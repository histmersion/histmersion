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
  type Slug = Slug.T
  object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Rare = Rare.T
  object Rare extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
