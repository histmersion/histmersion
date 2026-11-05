package com.histmersion.pluginhost

import com.histmersion.domain.Licence
import com.histmersion.plugin.LicenceReader

/** The host's reading of licence statements, lent to every plugin ([[com.histmersion.plugin.Plugin.Context.licences]]):
  * the one shared [[Licence]] classification the host judges every stated licence with.
  */
object HostLicences extends LicenceReader:
  type ReadNameName = ReadNameName.T
  object ReadNameName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadUrlUrl = ReadUrlUrl.T
  object ReadUrlUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadFirstStatements = ReadFirstStatements.T
  object ReadFirstStatements extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def read(l: Licence): com.histmersion.plugin.Licence.Read =
    com.histmersion.plugin.Licence.Read(l.id.value, l.family.wire, l.family.needsAttribution)

  def readName(name: com.histmersion.pluginhost.HostLicences.ReadNameName): com.histmersion.plugin.Licence.Read = read(Licence.classify(Option(name).getOrElse("")))
  def readUrl(url: com.histmersion.pluginhost.HostLicences.ReadUrlUrl): com.histmersion.plugin.Licence.Read = read(Licence.ofUrl(url))
  def readFirst(statements: List[com.histmersion.pluginhost.HostLicences.ReadFirstStatements]): com.histmersion.plugin.Licence.Read =
    read(Licence.firstKnown(statements))
