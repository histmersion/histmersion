package com.histmersion
package domain.library.acquisition
import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader
import com.histmersion.upstreams.landing.RepositoryLanding
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
      LandingSettings.entries(allowHosts.value).map(RepositoryLanding.HostPattern(_)),
      (LandingSettings.entries(denyHosts.value).map(RepositoryLanding.HostPattern(_)) ++ LandingSettings.entries(denyHostsExtra.value).map(RepositoryLanding.HostPattern(_))).distinct,
      LandingSettings.entries(academicHostWords.value).map(RepositoryLanding.HostWord(_)),
      LandingSettings.entries(generators.value).map(RepositoryLanding.Rules.Generators(_)),
      LandingSettings.entries(denyGenerators.value).map(RepositoryLanding.Rules.DenyGenerators(_)),
      LandingSettings.entries(denyHostWords.value).map(RepositoryLanding.HostWord(_)),
      LandingSettings.entries(oaPublisherHosts.value).map(RepositoryLanding.HostPattern(_))
    )

object LandingSettings:
  type Enabled = Enabled.T
  object Enabled extends RefinedType[Boolean, Pure]
  type MaxPages = MaxPages.T
  object MaxPages extends RefinedType[Int, Pure]
  type AllowHosts = AllowHosts.T
  object AllowHosts extends RefinedType[String, Pure]
  type DenyHosts = DenyHosts.T
  object DenyHosts extends RefinedType[String, Pure]
  type DenyHostsExtra = DenyHostsExtra.T
  object DenyHostsExtra extends RefinedType[String, Pure]
  type AcademicHostWords = AcademicHostWords.T
  object AcademicHostWords extends RefinedType[String, Pure]
  type Generators = Generators.T
  object Generators extends RefinedType[String, Pure]
  type DenyGenerators = DenyGenerators.T
  object DenyGenerators extends RefinedType[String, Pure]
  type DenyHostWords = DenyHostWords.T
  object DenyHostWords extends RefinedType[String, Pure]
  type OaPublisherHosts = OaPublisherHosts.T
  object OaPublisherHosts extends RefinedType[String, Pure]

  /** A comma-separated list's entries, trimmed and lower-case. */
  private def entries(list: String): List[String] =
    Option(list).getOrElse("").split(",").toList.map(_.trim.toLowerCase).filter(_.nonEmpty)
