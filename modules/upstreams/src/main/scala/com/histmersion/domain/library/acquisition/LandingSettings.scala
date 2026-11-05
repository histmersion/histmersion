package com.histmersion.domain.library.acquisition
import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader
import com.histmersion.upstreams.landing.RepositoryLanding

/** `histmersion.acquire.landing`: whether a fetch resolves repository landing pages, how many it reads per work, and
  * the host and platform lists of [[RepositoryLanding.Rules]] (each comma-separated).
  */
final case class LandingSettings(
    enabled: LandingSettings.Enabled,
    maxPages: LandingSettings.MaxPages,
    allowHosts: LandingSettings.AllowHosts,
    denyHosts: LandingSettings.DenyHosts,
    denyHostsExtra: LandingSettings.DenyHostsExtra,
    academicHostWords: LandingSettings.AcademicHostWords,
    generators: LandingSettings.Generators,
    denyGenerators: LandingSettings.DenyGenerators,
    denyHostWords: LandingSettings.DenyHostWords,
    oaPublisherHosts: LandingSettings.OaPublisherHosts
) derives ConfigReader:
  def rules: RepositoryLanding.Rules =
    RepositoryLanding.Rules(
      LandingSettings.entries(allowHosts),
      (LandingSettings.entries(denyHosts) ++ LandingSettings.entries(denyHostsExtra)).distinct,
      LandingSettings.entries(academicHostWords),
      LandingSettings.entries(generators),
      LandingSettings.entries(denyGenerators),
      LandingSettings.entries(denyHostWords),
      LandingSettings.entries(oaPublisherHosts)
    )

object LandingSettings:
  /** A comma-separated list's entries, trimmed and lower-case. */
  type Enabled = Enabled.T
  object Enabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxPages = MaxPages.T
  object MaxPages extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AllowHosts = AllowHosts.T
  object AllowHosts extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DenyHosts = DenyHosts.T
  object DenyHosts extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DenyHostsExtra = DenyHostsExtra.T
  object DenyHostsExtra extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AcademicHostWords = AcademicHostWords.T
  object AcademicHostWords extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Generators = Generators.T
  object Generators extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DenyGenerators = DenyGenerators.T
  object DenyGenerators extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DenyHostWords = DenyHostWords.T
  object DenyHostWords extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OaPublisherHosts = OaPublisherHosts.T
  object OaPublisherHosts extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def entries(list: String): List[String] =
    Option(list).getOrElse("").split(",").toList.map(_.trim.toLowerCase).filter(_.nonEmpty)
