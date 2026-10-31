package com.histmersion.pluginhost

import com.histmersion.domain.MediaLicence
import com.histmersion.plugin.{LicenceRead, LicenceReader}

/** The host's reading of licence statements, lent to every plugin ([[com.histmersion.plugin.PluginContext.licences]]):
  * the one [[MediaLicence]] the host judges every stated licence with.
  */
object HostLicences extends LicenceReader:
  private def read(l: MediaLicence.Licence): LicenceRead =
    LicenceRead(l.id, l.family, MediaLicence.needsAttribution(l.family))

  def readName(name: String): LicenceRead = read(MediaLicence.classify(Option(name).getOrElse("")))
  def readUrl(url: String): LicenceRead = read(MediaLicence.ofUrl(url))
  def readFirst(statements: List[String]): LicenceRead =
    read(MediaLicence.firstKnown(statements))
