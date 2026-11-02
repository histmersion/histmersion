package com.histmersion.domain.library.acquisition
import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader
import com.histmersion.upstreams.landing.RepositoryLanding

/** `histmersion.acquire.landing`: whether a fetch resolves repository landing pages, how many it reads per work, and
  * the host and platform lists of [[RepositoryLanding.Rules]] (each comma-separated).
  */
final case class LandingSettings(
    enabled: Boolean,
    maxPages: Int,
    allowHosts: String,
    denyHosts: String,
    denyHostsExtra: String,
    academicHostWords: String,
    generators: String,
    denyGenerators: String,
    denyHostWords: String,
    oaPublisherHosts: String
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
  def entries(list: String): List[String] =
    Option(list).getOrElse("").split(",").toList.map(_.trim.toLowerCase).filter(_.nonEmpty)
