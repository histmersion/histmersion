package com.histmersion.server.acquire

import com.histmersion.config.{AcquireConfig, LandingConfig}
import com.histmersion.domain.{HttpPolicy, RepositoryLanding}

extension (c: AcquireConfig)
  /** The identifier every acquisition upstream sees: the product, never the contact. */
  def agent: String = HttpPolicy.userAgent(c.userAgent)

extension (c: LandingConfig)
  def rules: RepositoryLanding.Rules =
    RepositoryLanding.Rules(
      c.allowHosts.entries,
      (c.denyHosts.entries ++ c.denyHostsExtra.entries).distinct,
      c.academicHostWords.entries,
      c.generators.entries,
      c.denyGenerators.entries,
      c.denyHostWords.entries,
      c.oaPublisherHosts.entries
    )
