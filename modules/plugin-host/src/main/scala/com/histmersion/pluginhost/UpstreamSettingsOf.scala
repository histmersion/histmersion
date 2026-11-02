package com.histmersion.pluginhost

import com.histmersion.config.{AcquireConfig, LandingConfig}
import com.histmersion.domain.library.acquisition.{CoreEndpoint, Endpoint, LandingSettings, SearchDefaults}
import com.histmersion.upstreams.settings.UpstreamSettings

extension (c: AcquireConfig)
  /** The upstreams' settings as `histmersion.acquire` holds them: what the host's own upstream clients are built with,
    * and what the first-party fetchers decode from the same keys.
    */
  def upstreamSettings: UpstreamSettings =
    UpstreamSettings(
      c.contactEmail,
      Endpoint(c.openalex.url),
      Endpoint(c.crossref.url),
      Endpoint(c.unpaywall.url),
      CoreEndpoint(c.core.url.value, c.core.apiKey.value),
      Endpoint(c.europepmc.url),
      Endpoint(c.pmcDataset.url),
      c.publicUserAgent,
      Endpoint(c.zenodo.url),
      Endpoint(c.datacite.url),
      Endpoint(c.doiHandles.url),
      c.landing.upstreamSettings,
      Endpoint(c.arxiv.url),
      Endpoint(c.archiveOrg.url),
      SearchDefaults(c.defaults.minYear.value)
    )

extension (l: LandingConfig)
  def upstreamSettings: LandingSettings =
    LandingSettings(
      l.enabled,
      l.maxPages.value,
      l.allowHosts.value,
      l.denyHosts.value,
      l.denyHostsExtra.value,
      l.academicHostWords.value,
      l.generators.value,
      l.denyGenerators.value,
      l.denyHostWords.value,
      l.oaPublisherHosts.value
    )
