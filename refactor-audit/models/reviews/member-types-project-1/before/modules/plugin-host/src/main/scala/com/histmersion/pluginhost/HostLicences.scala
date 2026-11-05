package com.histmersion.pluginhost

import com.histmersion.domain.Licence
import com.histmersion.plugin.LicenceReader

/** The host's reading of licence statements, lent to every plugin ([[com.histmersion.plugin.Plugin.Context.licences]]):
  * the one shared [[Licence]] classification the host judges every stated licence with.
  */
object HostLicences extends LicenceReader:
  private def read(l: Licence): com.histmersion.plugin.Licence.Read =
    com.histmersion.plugin.Licence.Read(l.id.value, l.family.wire, l.family.needsAttribution)

  def readName(name: String): com.histmersion.plugin.Licence.Read = read(Licence.classify(Option(name).getOrElse("")))
  def readUrl(url: String): com.histmersion.plugin.Licence.Read = read(Licence.ofUrl(url))
  def readFirst(statements: List[String]): com.histmersion.plugin.Licence.Read =
    read(Licence.firstKnown(statements))
