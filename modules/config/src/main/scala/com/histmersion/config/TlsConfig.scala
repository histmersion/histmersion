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
final case class TlsConfig(systemTrust: Boolean, extraCaFile: String) derives ConfigReader:
  def extraCa: Option[String] =
    Option(extraCaFile)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(p => if p.startsWith("~") then System.getProperty("user.home") + p.drop(1) else p)

  /** Anything beyond the JDK's roots asked for? */
  def custom: Boolean = systemTrust || extraCa.isDefined
