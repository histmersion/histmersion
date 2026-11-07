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
      UpstreamSettings.ContactEmail(c.contactEmail.value),
      Endpoint(Endpoint.Url(c.openalex.url.value)),
      Endpoint(Endpoint.Url(c.crossref.url.value)),
      Endpoint(Endpoint.Url(c.unpaywall.url.value)),
      CoreEndpoint(CoreEndpoint.Url(c.core.url.value), CoreEndpoint.ApiKey(c.core.apiKey.value)),
      Endpoint(Endpoint.Url(c.europepmc.url.value)),
      Endpoint(Endpoint.Url(c.pmcDataset.url.value)),
      UpstreamSettings.PublicUserAgent(c.publicUserAgent.value),
      Endpoint(Endpoint.Url(c.zenodo.url.value)),
      Endpoint(Endpoint.Url(c.datacite.url.value)),
      Endpoint(Endpoint.Url(c.doiHandles.url.value)),
      c.landing.upstreamSettings,
      Endpoint(Endpoint.Url(c.arxiv.url.value)),
      Endpoint(Endpoint.Url(c.archiveOrg.url.value)),
      SearchDefaults(SearchDefaults.MinYear(c.defaults.minYear.value))
    )

extension (l: LandingConfig)
  def upstreamSettings: LandingSettings =
    LandingSettings(
      LandingSettings.Enabled(l.enabled.value),
      LandingSettings.MaxPages(l.maxPages.value),
      LandingSettings.AllowHosts(l.allowHosts.value),
      LandingSettings.DenyHosts(l.denyHosts.value),
      LandingSettings.DenyHostsExtra(l.denyHostsExtra.value),
      LandingSettings.AcademicHostWords(l.academicHostWords.value),
      LandingSettings.Generators(l.generators.value),
      LandingSettings.DenyGenerators(l.denyGenerators.value),
      LandingSettings.DenyHostWords(l.denyHostWords.value),
      LandingSettings.OaPublisherHosts(l.oaPublisherHosts.value)
    )
