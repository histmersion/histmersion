package com.histmersion.server.acquire.fetchers

import com.histmersion.plugin.*

import hearth.kindlings.sconfigderivation.ConfigReader
import com.histmersion.plugin.PluginConfig.given
import com.histmersion.api.VocabularyCodecs.given

/** `oa-unpaywall`'s settings. `contact-email`: the e-mail Unpaywall requires with every request (its `email` parameter,
  * nothing else); `null` = Histmersion's own `acquire.contact-email`, and with neither Unpaywall is not asked (it
  * answers 422). Per install, never in the repository.
  */
final case class UnpaywallConfig(contactEmail: Option[String]) derives ConfigReader:
  def email: Option[String] = contactEmail.map(_.trim).filter(_.nonEmpty)
