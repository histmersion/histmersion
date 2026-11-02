package com.histmersion.domain.library.acquisition

import com.histmersion.domain.CoreQuota

/** Where the CORE budget is kept between restarts. */
trait CoreQuotaStore:
  def load(): Option[CoreQuota.State]
  def save(s: CoreQuota.State): Unit

object CoreQuotaStore:
  /** The metered upstreams' budget `core`. */
  def of(quotas: UpstreamQuotaService): CoreQuotaStore = new CoreQuotaStore:
    def load() = quotas.load(CoreBudget.Upstream)
    def save(s: CoreQuota.State) = quotas.save(CoreBudget.Upstream, s)
