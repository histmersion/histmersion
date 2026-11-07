package com.histmersion.web.settings

import com.raquo.laminar.api.L.*

/** Settings › Calibre and files: the Calibre library the command line reads and fills, and where the server keeps
  * uploads and backups.
  */
object CalibreSection:
  def pane: HtmlElement =
    div(
      cls := "settings-section",
      SettingsForm(
        com.histmersion.web.settings.SettingsForm.ApplyTitle("Calibre library"),
        Some(
          SettingsForm.Intro(
            "Calibre keeps the original of every book and paper. The command line (histmersion calibre …) reads and " +
              "fills this library on this computer; close the Calibre app while it writes."
          )
        ),
        List(
          SettingsForm.Item(com.histmersion.web.settings.SettingsForm.Item.Key("cli.calibre.library-dir"), Some(com.histmersion.web.settings.SettingsForm.Item.Name("Library folder"))),
          SettingsForm.Item(com.histmersion.web.settings.SettingsForm.Item.Key("cli.calibre.calibredb"), Some(com.histmersion.web.settings.SettingsForm.Item.Name("The calibredb command"))),
          SettingsForm.Item(com.histmersion.web.settings.SettingsForm.Item.Key("cli.calibre.originals-dir"), Some(com.histmersion.web.settings.SettingsForm.Item.Name("Folder of originals (optional)")))
        )
      ),
      SettingsForm(
        com.histmersion.web.settings.SettingsForm.ApplyTitle("Files on the server"),
        None,
        List(
          SettingsForm.Item(com.histmersion.web.settings.SettingsForm.Item.Key("converter.spool-dir"), Some(com.histmersion.web.settings.SettingsForm.Item.Name("Where an upload is kept for reading again"))),
          SettingsForm.Item(com.histmersion.web.settings.SettingsForm.Item.Key("backups.dir"), Some(com.histmersion.web.settings.SettingsForm.Item.Name("Backups folder"))),
          SettingsForm.Item(com.histmersion.web.settings.SettingsForm.Item.Key("bundles.dir"), Some(com.histmersion.web.settings.SettingsForm.Item.Name("Exported collections folder")))
        )
      )
    )
