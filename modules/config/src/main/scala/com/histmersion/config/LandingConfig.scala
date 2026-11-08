package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** Repository landing pages (#1580, [[RepositoryLanding]]): whether the fetch resolves them one step, how many it reads
  * per work, and the host and platform lists that decide what is a repository (each comma-separated; a host matches
  * itself and its subdomains). `denyHosts` — publishers, and sites that are not repositories — wins over every other
  * signal; `denyHostsExtra` is added to it, so a user's config can deny more hosts without restating the shipped list.
  */
final case class LandingConfig(
    enabled: LandingConfig.Enabled,
    maxPages: ItemCount,
    allowHosts: CommaList,
    denyHosts: CommaList,
    denyHostsExtra: CommaList,
    academicHostWords: CommaList,
    generators: CommaList,
    denyGenerators: CommaList,
    denyHostWords: CommaList,
    /** Open-access publishers whose open locations are read (#2550, [[RepositoryLanding.oaPublisherPage]]). */
    oaPublisherHosts: CommaList,
    /** Publishers that refuse scripts: their gold open-access works go to the acquisition list as a download in the
      * browser ([[BrowserDownload]]).
      */
    browserDownloadHosts: CommaList = CommaList("")
) derives ConfigReader:
  def refusingHosts: List[LandingConfig.RefusingHost] =
    browserDownloadHosts.entries.map(e => LandingConfig.RefusingHost(e.value))

object LandingConfig:
  type RefusingHost = RefusingHost.T
  object RefusingHost extends Newtype[String, Pure]

  type Enabled = Enabled.T
  object Enabled extends Newtype[Boolean, Pure]
