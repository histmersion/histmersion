package com.histmersion
package pluginhost

import com.histmersion.domain.Licence
import com.histmersion.plugin.LicenceReader
import com.histmersion.lint.{tolerated, LintRule}

/** The host's reading of licence statements, lent to every plugin ([[com.histmersion.plugin.Plugin.Context.licences]]):
  * the one shared [[Licence]] classification the host judges every stated licence with.
  */
object HostLicences extends LicenceReader:
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def read(l: Licence): plugin.Licence.Read =
    plugin.Licence.Read(
      plugin.Licence.Read.Id(l.id.value),
      plugin.Licence.Read.Family(l.family.wire.value),
      plugin.Licence.Read.NeedsAttribution(l.family.needsAttribution)
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def readName(name: LicenceReader.ReadNameName): plugin.Licence.Read =
    read(Licence.classify(Licence.Id(Option(name).fold("")(_.value))))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def readUrl(url: LicenceReader.ReadUrlUrl): plugin.Licence.Read =
    read(Licence.ofUrl(Licence.Url(url.value)))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def readFirst(statements: List[LicenceReader.ReadFirstStatements]): plugin.Licence.Read =
    read(Licence.firstKnown(statements.map(s => Licence.Statement(s.value))))
