package com.histmersion.plugins.calibre

import com.histmersion.testsupport.ComparedByValue.given

import java.nio.file.Path

/** How the plugin finds `calibredb`: a LaunchAgent's `PATH` holds only the system directories, so a bare name is also
  * looked up where Calibre installs it.
  */
class CalibreSettingsSuite extends munit.FunSuite:

  private given com.histmersion.plugin.LocalFiles = new com.histmersion.pluginhost.DiskLocalFiles
  private given com.histmersion.plugin.Processes = new com.histmersion.pluginhost.SystemProcesses
  private val settings = Calibre.Settings(com.histmersion.plugins.calibre.Calibre.Settings.LibraryPath("~/Calibre Library"), com.histmersion.plugins.calibre.Calibre.Settings.Calibredb("calibredb"), com.histmersion.plugins.calibre.Calibre.Settings.Sqlite("sqlite3"), writeBack = com.histmersion.plugins.calibre.Calibre.Settings.WriteBack(true), com.histmersion.plugins.calibre.Calibre.Settings.GuiProcess("gui"))
  private def only(paths: String*): Path => Boolean = p => paths.contains(p.toString)

  test("a bare name on the search path wins over the install directories"):
    val lib = settings.library(
      Some(com.histmersion.plugin.Plugin.Context.EnvResult("/home/someone")),
      Some(com.histmersion.plugin.Plugin.Context.EnvResult("/usr/bin:/custom/bin")),
      only("/custom/bin/calibredb", "/Applications/calibre.app/Contents/MacOS/calibredb")
    )
    assertEquals(lib.calibredb, "/custom/bin/calibredb")
    assertEquals(lib.library, "/home/someone/Calibre Library")

  test("a bare name not on the search path is found in the macOS app"):
    val lib = settings.library(
      Some(com.histmersion.plugin.Plugin.Context.EnvResult("/home/someone")),
      Some(com.histmersion.plugin.Plugin.Context.EnvResult("/usr/bin:/bin")),
      only("/Applications/calibre.app/Contents/MacOS/calibredb")
    )
    assertEquals(lib.calibredb, "/Applications/calibre.app/Contents/MacOS/calibredb")

  test("the user's own Applications folder is read against the home directory"):
    val lib = settings.library(
      Some(com.histmersion.plugin.Plugin.Context.EnvResult("/home/someone")),
      None,
      only("/home/someone/Applications/calibre.app/Contents/MacOS/calibredb")
    )
    assertEquals(lib.calibredb, "/home/someone/Applications/calibre.app/Contents/MacOS/calibredb")

  test("found nowhere, the name stays as configured"):
    assertEquals(settings.library(Some(com.histmersion.plugin.Plugin.Context.EnvResult("/home/someone")), Some(com.histmersion.plugin.Plugin.Context.EnvResult("/usr/bin")), only()).calibredb, "calibredb")

  test("a configured path is used as it is"):
    val lib = Calibre
      .Settings(com.histmersion.plugins.calibre.Calibre.Settings.LibraryPath("~/Calibre Library"), com.histmersion.plugins.calibre.Calibre.Settings.Calibredb("/somewhere/calibredb"), com.histmersion.plugins.calibre.Calibre.Settings.Sqlite("sqlite3"), writeBack = com.histmersion.plugins.calibre.Calibre.Settings.WriteBack(true), com.histmersion.plugins.calibre.Calibre.Settings.GuiProcess("gui"))
      .library(None, Some(com.histmersion.plugin.Plugin.Context.EnvResult("/usr/bin")), _ => true)
    assertEquals(lib.calibredb, "/somewhere/calibredb")
