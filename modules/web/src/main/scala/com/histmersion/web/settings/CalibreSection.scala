package com.histmersion
package web.settings

import com.raquo.laminar.api.L.*

/** Settings › Calibre and files: the Calibre library the command line reads and fills, and where the server keeps
  * uploads and backups.
  */
object CalibreSection:
  def pane: HtmlElement =
    div(
      cls := "settings-section",
      SettingsForm(
        SettingsForm.ApplyTitle("Calibre library"),
        Some(
          SettingsForm.Intro(
            "Calibre keeps the original of every book and paper. The command line (histmersion calibre …) reads and " +
              "fills this library on this computer; close the Calibre app while it writes."
          )
        ),
        List(
          SettingsForm.Item(SettingsForm.Item.Key("cli.calibre.library-dir"), Some(SettingsForm.Item.Name("Library folder"))),
          SettingsForm.Item(SettingsForm.Item.Key("cli.calibre.calibredb"), Some(SettingsForm.Item.Name("The calibredb command"))),
          SettingsForm.Item(SettingsForm.Item.Key("cli.calibre.originals-dir"), Some(SettingsForm.Item.Name("Folder of originals (optional)")))
        )
      ),
      SettingsForm(
        SettingsForm.ApplyTitle("Files on the server"),
        None,
        List(
          SettingsForm.Item(SettingsForm.Item.Key("converter.spool-dir"), Some(SettingsForm.Item.Name("Where an upload is kept for reading again"))),
          SettingsForm.Item(SettingsForm.Item.Key("backups.dir"), Some(SettingsForm.Item.Name("Backups folder"))),
          SettingsForm.Item(SettingsForm.Item.Key("bundles.dir"), Some(SettingsForm.Item.Name("Exported collections folder")))
        )
      )
    )
