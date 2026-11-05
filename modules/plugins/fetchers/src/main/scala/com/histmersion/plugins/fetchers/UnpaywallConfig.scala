package com.histmersion.plugins.fetchers

import com.histmersion.plugin.Plugin.Config.given
import com.histmersion.upstreams.settings.UpstreamSettings
import hearth.kindlings.sconfigderivation.ConfigReader

/** `oa-unpaywall`'s settings. `contact-email`: the e-mail Unpaywall requires with every request (its `email` parameter,
  * nothing else); `null` = Histmersion's own `acquire.contact-email`, and with neither Unpaywall is not asked (it
  * answers 422). Per install, never in the repository.
  */
final case class UnpaywallConfig(contactEmail: Option[UnpaywallConfig.ContactEmail], acquire: UpstreamSettings) derives ConfigReader:
  /** The contact Unpaywall is asked with: the plugin's own, else Histmersion's. */
  def email: Option[com.histmersion.plugins.fetchers.UnpaywallConfig.EmailResult] = UnpaywallConfig.own(contactEmail).orElse(acquire.contact)

object UnpaywallConfig:
  /** A contact as a block states it: trimmed, a blank one none. */
  type EmailResult = EmailResult.T
  object EmailResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OwnResult = OwnResult.T
  object OwnResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OwnContact = OwnContact.T
  object OwnContact extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ContactEmail = ContactEmail.T
  object ContactEmail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def own(contact: Option[com.histmersion.plugins.fetchers.UnpaywallConfig.OwnContact]): Option[com.histmersion.plugins.fetchers.UnpaywallConfig.OwnResult] = contact.map(_.trim).filter(_.nonEmpty)
