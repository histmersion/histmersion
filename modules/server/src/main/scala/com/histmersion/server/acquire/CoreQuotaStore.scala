package com.histmersion.server.acquire

import com.histmersion.domain.CoreQuota

import javax.sql.DataSource

/** Where the CORE budget is kept between restarts. */
trait CoreQuotaStore:
  def load(): Option[CoreQuota.State]
  def save(s: CoreQuota.State): Unit

object CoreQuotaStore:
  /** The `upstream_quota` row `core`. */
  def db(ds: DataSource): CoreQuotaStore = new CoreQuotaStore:
    def load() = com.histmersion.db.UpstreamQuotaRepo.load(ds, CoreBudget.Upstream)
    def save(s: CoreQuota.State) = com.histmersion.db.UpstreamQuotaRepo.save(ds, CoreBudget.Upstream, s)
