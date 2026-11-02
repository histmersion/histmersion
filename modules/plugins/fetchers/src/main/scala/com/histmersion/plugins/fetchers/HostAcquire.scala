package com.histmersion.plugins.fetchers

import com.histmersion.upstreams.settings.UpstreamSettings
import hearth.kindlings.sconfigderivation.ConfigReader

/** The settings of a fetcher plugin with none of its own: the host's `histmersion.acquire`, which the host supplies. */
final case class HostAcquire(acquire: UpstreamSettings) derives ConfigReader
