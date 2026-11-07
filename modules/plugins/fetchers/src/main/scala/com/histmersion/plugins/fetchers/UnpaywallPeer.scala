package com.histmersion.plugins.fetchers

import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** `oa-unpaywall`'s contact, as its block has it in effect (`null`: none of its own). */
final case class UnpaywallPeer(contactEmail: Option[UnpaywallConfig.ContactEmail] = None) derives ConfigReader
