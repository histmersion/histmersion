package com.histmersion.plugins.fetchers

import hearth.kindlings.sconfigderivation.ConfigReader

/** What `oa-landing` reads of the other open-access index plugins' blocks. */
final case class LandingPeers(oaUnpaywall: UnpaywallPeer = UnpaywallPeer()) derives ConfigReader
