package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Repository landing pages (#1580, [[RepositoryLanding]]): whether the fetch resolves them one step, how many it reads
  * per work, and the host and platform lists that decide what is a repository (each comma-separated; a host matches
  * itself and its subdomains). `denyHosts` — publishers, and sites that are not repositories — wins over every other
  * signal; `denyHostsExtra` is added to it, so a user's config can deny more hosts without restating the shipped list.
  */
final case class LandingConfig(
    enabled: Boolean,
    maxPages: Int,
    allowHosts: String,
    denyHosts: String,
    denyHostsExtra: String,
    academicHostWords: String,
    generators: String,
    denyGenerators: String,
    denyHostWords: String,
    /** Open-access publishers whose open locations are read (#2550, [[RepositoryLanding.oaPublisherPage]]). */
    oaPublisherHosts: String,
    /** Publishers that refuse scripts: their gold open-access works go to the acquisition list as a download in the
      * browser ([[BrowserDownload]]).
      */
    browserDownloadHosts: String = ""
) derives ConfigReader:
  def refusingHosts: List[String] = LandingConfig.split(browserDownloadHosts)

object LandingConfig:
  def split(s: String): List[String] =
    Option(s).toList.flatMap(_.split(",")).map(_.trim.toLowerCase).filter(_.nonEmpty)
