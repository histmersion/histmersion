package com.histmersion.plugins.fetchers

import com.histmersion.plugin.Plugin.Config.given
import com.histmersion.upstreams.settings.UpstreamSettings
import hearth.kindlings.sconfigderivation.ConfigReader

/** `oa-unpaywall`'s settings. `contact-email`: the e-mail Unpaywall requires with every request (its `email` parameter,
  * nothing else); `null` = Histmersion's own `acquire.contact-email`, and with neither Unpaywall is not asked (it
  * answers 422). Per install, never in the repository.
  */
final case class UnpaywallConfig(contactEmail: Option[String], acquire: UpstreamSettings) derives ConfigReader:
  /** The contact Unpaywall is asked with: the plugin's own, else Histmersion's. */
  def email: Option[String] = UnpaywallConfig.own(contactEmail).orElse(acquire.contact)

object UnpaywallConfig:
  /** A contact as a block states it: trimmed, a blank one none. */
  def own(contact: Option[String]): Option[String] = contact.map(_.trim).filter(_.nonEmpty)
