package com.histmersion
package domain.library.acquisition

import com.histmersion.config.{AcquireConfig, LandingConfig}
import com.histmersion.domain.HttpPolicy
import com.histmersion.upstreams.landing.RepositoryLanding
import com.histmersion.lint.{tolerated, LintRule}

extension (c: AcquireConfig)
  /** The identifier every acquisition upstream sees: the product, never the contact. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def agent: String = HttpPolicy.userAgent(HttpPolicy.UserAgent(c.userAgent.value)).value

extension (c: LandingConfig)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def rules: RepositoryLanding.Rules =
    RepositoryLanding.Rules(
      c.allowHosts.entries.map(e => RepositoryLanding.HostPattern(e.value)),
      (c.denyHosts.entries ++ c.denyHostsExtra.entries).distinct.map(e => RepositoryLanding.HostPattern(e.value)),
      c.academicHostWords.entries.map(e => RepositoryLanding.HostWord(e.value)),
      c.generators.entries.map(e => RepositoryLanding.Rules.Generators(e.value)),
      c.denyGenerators.entries.map(e => RepositoryLanding.Rules.DenyGenerators(e.value)),
      c.denyHostWords.entries.map(e => RepositoryLanding.HostWord(e.value)),
      c.oaPublisherHosts.entries.map(e => RepositoryLanding.HostPattern(e.value))
    )
