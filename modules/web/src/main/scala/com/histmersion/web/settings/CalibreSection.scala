package com.histmersion.web.settings

import com.raquo.laminar.api.L.*

/** Settings › Calibre and files: the Calibre library the command line reads and fills, and where the server
  * keeps uploads and backups. */
object CalibreSection:
  def pane: HtmlElement =
    div(cls := "settings-section",
      SettingsForm("Calibre library",
        Some("Calibre keeps the original of every book and paper. The command line (histmersion calibre …) reads and " +
          "fills this library on this computer; close the Calibre app while it writes."),
        List(SettingsForm.Item("cli.calibre.library-dir", Some("Library folder")),
          SettingsForm.Item("cli.calibre.calibredb", Some("The calibredb command")),
          SettingsForm.Item("cli.calibre.originals-dir", Some("Folder of originals (optional)")))),
      SettingsForm("Files on the server", None,
        List(SettingsForm.Item("converter.spool-dir", Some("Where an upload is kept for reading again")),
          SettingsForm.Item("backups.dir", Some("Backups folder")),
          SettingsForm.Item("bundles.dir", Some("Exported collections folder")))))
