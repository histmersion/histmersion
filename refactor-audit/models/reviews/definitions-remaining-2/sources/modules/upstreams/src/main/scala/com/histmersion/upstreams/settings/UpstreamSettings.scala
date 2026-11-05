package com.histmersion.upstreams.settings
import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader
import com.histmersion.domain.library.acquisition.{CoreEndpoint, Endpoint, LandingSettings, SearchDefaults}

/** The acquisition upstreams as `histmersion.acquire` configures them: their endpoints, the landing-page rules, the
  * contact e-mail and the identity the PMC hosts see. Decoded by the first-party fetcher plugins from the settings the
  * host supplies them, and built by the host from its own configuration for its own calls: the same keys either way.
  * The contact e-mail goes ONLY into the `mailto`/`email` parameters Crossref, OpenAlex and Unpaywall define for it.
  */
final case class UpstreamSettings(
    contactEmail: UpstreamSettings.ContactEmail,
    openalex: Endpoint,
    crossref: Endpoint,
    unpaywall: Endpoint,
    core: CoreEndpoint,
    europepmc: Endpoint,
    pmcDataset: Endpoint,
    publicUserAgent: UpstreamSettings.PublicUserAgent,
    zenodo: Endpoint,
    datacite: Endpoint,
    doiHandles: Endpoint,
    landing: LandingSettings,
    arxiv: Endpoint,
    archiveOrg: Endpoint,
    defaults: SearchDefaults
) derives ConfigReader:

  /** The contact e-mail, when one is configured: a blank one means anonymous (no polite pool, no Unpaywall). */
  def contact: Option[String] = Option(contactEmail).map(_.trim).filter(_.nonEmpty)

  /** The `mailto` value; empty without a contact, and [[Url]] drops an empty parameter. */
  def mailto: String = contact.getOrElse("")

object UpstreamSettings:
  type ContactEmail = ContactEmail.T
  object ContactEmail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PublicUserAgent = PublicUserAgent.T
  object PublicUserAgent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
