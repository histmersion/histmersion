package com.histmersion.pluginhost

import com.histmersion.domain.Licence
import com.histmersion.plugin.{LicenceRead, LicenceReader}

/** The host's reading of licence statements, lent to every plugin ([[com.histmersion.plugin.PluginContext.licences]]):
  * the one shared [[Licence]] classification the host judges every stated licence with.
  */
object HostLicences extends LicenceReader:
  private def read(l: Licence): LicenceRead = LicenceRead(l.id.value, l.family.wire, l.family.needsAttribution)

  def readName(name: String): LicenceRead = read(Licence.classify(Option(name).getOrElse("")))
  def readUrl(url: String): LicenceRead = read(Licence.ofUrl(url))
  def readFirst(statements: List[String]): LicenceRead =
    read(Licence.firstKnown(statements))
