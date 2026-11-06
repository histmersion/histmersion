package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Which certificate authorities an acquisition request trusts beyond the JDK's own (#1568). Both are opt-in and ADD
  * roots; neither turns verification off ([[HttpPolicy.trust]]).
  *
  * @param systemTrust
  *   also trust the operating system's roots — on macOS the system keychains (read with `security`, which works in the
  *   native image; the JDK's `KeychainStore-ROOT` does not), on Linux the distribution's CA bundle
  * @param extraCaFile
  *   a PEM file of further roots (`~` expanded); "" = none
  */
final case class TlsConfig(systemTrust: TlsConfig.SystemTrust, extraCaFile: TlsConfig.ExtraCaFile) derives ConfigReader:
  def extraCa: Option[com.histmersion.config.TlsConfig.ExtraCaFile] =
    Option(extraCaFile)
      .map(_.value.trim)
      .filter(_.nonEmpty)
      .map(p => if p.startsWith("~") then System.getProperty("user.home") + p.drop(1) else p)
      .map(TlsConfig.ExtraCaFile(_))

  /** Anything beyond the JDK's roots asked for? */
  def custom: Boolean = systemTrust.value || extraCa.isDefined

object TlsConfig:

  type SystemTrust = SystemTrust.T
  object SystemTrust extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtraCaFile = ExtraCaFile.T
  object ExtraCaFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
