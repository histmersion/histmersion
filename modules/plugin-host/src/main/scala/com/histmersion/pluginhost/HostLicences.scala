package com.histmersion
package pluginhost

import com.histmersion.domain.Licence
import com.histmersion.plugin.LicenceReader

/** The host's reading of licence statements, lent to every plugin ([[com.histmersion.plugin.Plugin.Context.licences]]):
  * the one shared [[Licence]] classification the host judges every stated licence with.
  */
object HostLicences extends LicenceReader:
  private def read(l: Licence): com.histmersion.plugin.Licence.Read =
    com.histmersion.plugin.Licence.Read(
      com.histmersion.plugin.Licence.Read.Id(l.id.value),
      com.histmersion.plugin.Licence.Read.Family(l.family.wire.value),
      com.histmersion.plugin.Licence.Read.NeedsAttribution(l.family.needsAttribution)
    )

  def readName(name: LicenceReader.ReadNameName): com.histmersion.plugin.Licence.Read =
    read(Licence.classify(Licence.Id(Option(name).fold("")(_.value))))
  def readUrl(url: LicenceReader.ReadUrlUrl): com.histmersion.plugin.Licence.Read =
    read(Licence.ofUrl(Licence.Url(url.value)))
  def readFirst(statements: List[LicenceReader.ReadFirstStatements]): com.histmersion.plugin.Licence.Read =
    read(Licence.firstKnown(statements.map(s => Licence.Statement(s.value))))
