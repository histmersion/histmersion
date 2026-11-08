package com.histmersion
package plugins.calibre

import com.histmersion.testsupport.ComparedByValue.given

import java.nio.file.Path
import com.histmersion.plugin.LocalFiles
import com.histmersion.pluginhost.DiskLocalFiles
import com.histmersion.plugin.Processes
import com.histmersion.pluginhost.SystemProcesses
import com.histmersion.plugin.Plugin

/** How the plugin finds `calibredb`: a LaunchAgent's `PATH` holds only the system directories, so a bare name is also
  * looked up where Calibre installs it.
  */
class CalibreSettingsSuite extends munit.FunSuite:

  private given LocalFiles = new DiskLocalFiles
  private given Processes = new SystemProcesses
  private val settings = Calibre.Settings(
    Calibre.Settings.LibraryPath("~/Calibre Library"),
    Calibre.Settings.Calibredb("calibredb"),
    Calibre.Settings.Sqlite("sqlite3"),
    writeBack = Calibre.Settings.WriteBack(true),
    Calibre.Settings.GuiProcess("gui")
  )
  private def only(paths: String*): Path => Boolean = p => paths.contains(p.toString)

  test("a bare name on the search path wins over the install directories"):
    val lib = settings.library(
      Some(Plugin.Context.EnvResult("/home/someone")),
      Some(Plugin.Context.EnvResult("/usr/bin:/custom/bin")),
      only("/custom/bin/calibredb", "/Applications/calibre.app/Contents/MacOS/calibredb")
    )
    assertEquals(lib.calibredb, "/custom/bin/calibredb")
    assertEquals(lib.library, "/home/someone/Calibre Library")

  test("a bare name not on the search path is found in the macOS app"):
    val lib = settings.library(
      Some(Plugin.Context.EnvResult("/home/someone")),
      Some(Plugin.Context.EnvResult("/usr/bin:/bin")),
      only("/Applications/calibre.app/Contents/MacOS/calibredb")
    )
    assertEquals(lib.calibredb, "/Applications/calibre.app/Contents/MacOS/calibredb")

  test("the user's own Applications folder is read against the home directory"):
    val lib = settings.library(
      Some(Plugin.Context.EnvResult("/home/someone")),
      None,
      only("/home/someone/Applications/calibre.app/Contents/MacOS/calibredb")
    )
    assertEquals(lib.calibredb, "/home/someone/Applications/calibre.app/Contents/MacOS/calibredb")

  test("found nowhere, the name stays as configured"):
    assertEquals(
      settings
        .library(Some(Plugin.Context.EnvResult("/home/someone")), Some(Plugin.Context.EnvResult("/usr/bin")), only())
        .calibredb,
      "calibredb"
    )

  test("a configured path is used as it is"):
    val lib = Calibre
      .Settings(
        Calibre.Settings.LibraryPath("~/Calibre Library"),
        Calibre.Settings.Calibredb("/somewhere/calibredb"),
        Calibre.Settings.Sqlite("sqlite3"),
        writeBack = Calibre.Settings.WriteBack(true),
        Calibre.Settings.GuiProcess("gui")
      )
      .library(None, Some(Plugin.Context.EnvResult("/usr/bin")), _ => true)
    assertEquals(lib.calibredb, "/somewhere/calibredb")
