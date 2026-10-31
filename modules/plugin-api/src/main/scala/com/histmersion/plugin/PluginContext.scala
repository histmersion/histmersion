package com.histmersion.plugin

import scala.compiletime.deferred

/** What the host lends a plugin, and where it plugs in. Each registry is one extension point; a new extension point is
  * a new registry here, not a new service type, so existing plugin jars keep loading. (Six: [[secretVaults]],
  * [[sourceFetchers]], [[mediaCollections]], [[eventCatalogues]], [[aiProviders]] and [[gazetteerSources]]. Each one
  * added after the first has a default that refuses every addition, so a context written for an earlier extension point
  * — a test's — still compiles, and adding them changed no `Spi.Version`.)
  */
trait PluginContext:
  /** The host's outbound HTTP — the one policy (User-Agent without personal data, Retry-After, bot challenges stop the
    * request). A plugin never opens its own sockets.
    */
  def http: PluginHttp

  def log: PluginLog

  /** The host's environment as it was read at startup (on the main thread: a native image reads it once). For what a
    * plugin needs to run a tool (`PATH`), never for credentials — those are [[Secret]]s in the config.
    */
  def env(name: String): Option[String]

  /** A secret's value. Every [[Secret]] inside the decoded config was resolved before [[Plugin.register]] was called,
    * so for those this is a lookup; another one is resolved now. Never log the value.
    */
  def secret(s: Secret): Either[String, String]

  /** Named secret backends: `<name>:<ref>` references are resolved by the vault registered as `name`. */
  def secretVaults: Registry[SecretVault]

  /** The acquisition chain: each fetcher under its own id, which is what `histmersion.acquire.chain` orders. A plugin
    * may add several (one per step). A host that has no acquisition chain refuses every addition — the default, so a
    * context written for an earlier extension point (a test's) still compiles.
    */
  def sourceFetchers: Registry[SourceFetcher] = Registry.refusing("this host has no acquisition chain")

  /** The media collections `histmersion media import <id>` runs: each under its own id — the name the import is asked
    * for, and the provider its pictures are stored under. The host judges every licence and every match itself
    * ([[MediaCollection]]).
    */
  def mediaCollections: Registry[MediaCollection] = Registry.refusing("this host has no media import")

  /** The event catalogues `histmersion events import <id>` reads, each under its own id — the import's name and the
    * catalogue its records are stored under.
    */
  def eventCatalogues: Registry[EventCatalogue] = Registry.refusing("this host has no event catalogues")

  /** The AI providers the embedded agents call, each under its PROVIDER NAME — the part before the colon of a
    * `provider:model` reference. The host runs the tool loop, the tools, the validation and every budget; the provider
    * answers one model call at a time.
    */
  def aiProviders: Registry[AiProvider] = Registry.refusing("this host has no AI providers")

  /** The host's budget for the metered upstream `upstream` (`core`: CORE's API), shared with the host's own calls to it
    * and kept for the whole server. An upstream the host keeps no budget for gets [[UpstreamQuota.Unlimited]] — the
    * default, so a context written before budgets existed still compiles.
    */
  def quota(upstream: String): UpstreamQuota = UpstreamQuota.Unlimited

  /** The host's reading of licence statements ([[LicenceReader]]), for a media collection that must decide on a licence
    * before it states it. A host that lends none reads every statement as unknown — the default, so a context written
    * before it existed still compiles.
    */
  def licences: LicenceReader = LicenceReader.Unread

  /** The gazetteer datasets `histmersion places fetch <id>` imports, each under its own id — the name the import is
    * asked for. A host that imports no gazetteers refuses every addition — the default, so a context written for an
    * earlier extension point still compiles.
    */
  def gazetteerSources: Registry[GazetteerSource] = Registry.refusing("this host has no gazetteer import")
