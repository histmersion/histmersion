package com.histmersion.plugins.fetchers

import com.histmersion.upstreams.settings.UpstreamSettings
import hearth.kindlings.sconfigderivation.ConfigReader

/** `oa-landing`'s settings: the host's `histmersion.acquire` (its landing-page rules among them), and the contact
  * `oa-unpaywall` asks Unpaywall with — the landing step reads Unpaywall's record with the very same contact, so the
  * two share ONE request per work.
  */
final case class LandingConfig(acquire: UpstreamSettings, plugins: LandingPeers = LandingPeers()) derives ConfigReader:
  /** The contact Unpaywall is asked with on `oa-unpaywall`'s behalf: its own, else Histmersion's. */
  def unpaywallEmail: Option[com.histmersion.plugins.fetchers.LandingConfig.UnpaywallEmailResult] =
    UnpaywallConfig.own(plugins.oaUnpaywall.contactEmail).orElse(acquire.contact)

object LandingConfig:
  type UnpaywallEmailResult = UnpaywallEmailResult.T
  object UnpaywallEmailResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
