package com.histmersion.server.acquire

import com.histmersion.config.{AcquireConfig, LandingConfig}
import com.histmersion.domain.{HttpPolicy, RepositoryLanding}

extension (c: AcquireConfig)
  /** The identifier every acquisition upstream sees: the product, never the contact. */
  def agent: String = HttpPolicy.userAgent(c.userAgent)

extension (c: LandingConfig)
  def rules: RepositoryLanding.Rules =
    RepositoryLanding.Rules(LandingConfig.split(c.allowHosts),
      (LandingConfig.split(c.denyHosts) ++ LandingConfig.split(c.denyHostsExtra)).distinct,
      LandingConfig.split(c.academicHostWords), LandingConfig.split(c.generators), LandingConfig.split(c.denyGenerators),
      LandingConfig.split(c.denyHostWords), LandingConfig.split(c.oaPublisherHosts))
