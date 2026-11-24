package com.histmersion
package plugins.fetchers

import com.histmersion.plugin.Plugin.Config.given
import com.histmersion.upstreams.settings.UpstreamSettings
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** `oa-unpaywall`'s settings. `contact-email`: the e-mail Unpaywall requires with every request (its `email` parameter,
  * nothing else); `null` = Histmersion's own `acquire.contact-email`, and with neither Unpaywall is not asked (it
  * answers 422). Per install, never in the repository.
  */
final case class UnpaywallConfig(contactEmail: Option[UnpaywallConfig.ContactEmail], acquire: UpstreamSettings)
    derives ConfigReader:
  /** The contact Unpaywall is asked with: the plugin's own, else Histmersion's. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def email: Option[UnpaywallConfig.ContactEmail] =
    UnpaywallConfig.own(contactEmail).orElse(acquire.contact.map(c => UnpaywallConfig.ContactEmail(c.value)))

object UnpaywallConfig:
  /** A contact as a block states it: trimmed, a blank one none. */

  type ContactEmail = ContactEmail.T
  object ContactEmail extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def own(contact: Option[ContactEmail]): Option[ContactEmail] =
    contact.map(c => ContactEmail(c.value.trim)).filter(_.value.nonEmpty)
