package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** Which certificate authorities an acquisition request trusts beyond the JDK's own (#1568). Both are opt-in and ADD
  * roots; neither turns verification off ([[HttpPolicy.trust]]).
  *
  * @param systemTrust
  *   also trust the operating system's roots — on macOS the system keychains (read with `security`, which works in the
  *   native image; the JDK's `KeychainStore-ROOT` does not), on Linux the distribution's CA bundle
  * @param extraCaFile
  *   a PEM file of further roots (`~` expanded); "" = none
  */
@movesTo(FeaturePlace.Config)
final case class TlsConfig(systemTrust: TlsConfig.SystemTrust, extraCaFile: TlsConfig.ExtraCaFile) derives ConfigReader:
  @tolerated(LintRule.Global, "pending G15 (no globals; everything from config)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def extraCa: Option[TlsConfig.ExtraCaFile] =
    Option(extraCaFile)
      .map(_.value.trim)
      .filter(_.nonEmpty)
      .map(p => if p.startsWith("~") then System.getProperty("user.home") + p.drop(1) else p)
      .map(TlsConfig.ExtraCaFile(_))

  /** Anything beyond the JDK's roots asked for? */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def custom: Boolean = systemTrust.value || extraCa.isDefined

object TlsConfig:

  type SystemTrust = SystemTrust.T
  object SystemTrust extends Newtype[Boolean, Pure]
  type ExtraCaFile = ExtraCaFile.T
  object ExtraCaFile extends Newtype[String, Pure]
