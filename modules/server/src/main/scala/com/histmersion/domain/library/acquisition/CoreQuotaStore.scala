package com.histmersion
package domain.library.acquisition

import com.histmersion.domain.library.acquisition.State

import com.histmersion.domain.CoreQuota
import com.histmersion.util.database.WritePermit

/** Where the CORE budget is kept between restarts. */
trait CoreQuotaStore:
  def load(): Option[State]

  /** Keep `s`. The budget is spent from any thread a plugin calls CORE on, so the write takes the permit of the
    * operation or worker unit it runs in, or one of its own; while a restore runs it is refused (thrown), and the
    * budget stays in memory.
    */
  def save(s: State): Unit

object CoreQuotaStore:
  /** The metered upstreams' budget `core`, written under `gate`. */
  def of(quotas: UpstreamQuotaService, gate: WritePermit.Gate): CoreQuotaStore = new CoreQuotaStore:
    def load() = quotas.load(CoreBudget.Upstream)
    def save(s: State) = gate.writing(quotas.save(CoreBudget.Upstream, s))
