package com.histmersion.plugin

import hearth.kindlings.sconfigderivation.ConfigReader

/** The plugin SPI, spike version (docs/research-plugins.md §2). Everything a plugin author compiles
  * against; the host owns the rest. Deliberately small: a descriptor, a config reader, and one
  * factory per kind. */

/** What a plugin contributes. One ServiceLoader service per kind (`META-INF/services/<the kind's
  * abstract class>`), so the host asks for exactly the kind it is wiring and gets it typed. */
enum PluginKind:
  case SourceFetcher, MediaCollection, EventCatalogue, Gazetteer

/** @param id         the config key (`histmersion.plugins.<id>`) and the name in logs; kebab-case
  * @param spiVersion the SPI generation the plugin was compiled against; the host refuses others */
final case class PluginInfo(id: String, kind: PluginKind, version: String, spiVersion: Int)

object Spi:
  /** Bumped on an incompatible change to anything in this package. */
  val Version = 1

/** Every plugin. `Config` is the plugin's own case class with a derived reader; the host decodes the
  * plugin's block with [[configReader]] and hands the typed value to the kind's factory — a plugin
  * never sees the raw HOCON. */
trait Plugin:
  type Config
  def info: PluginInfo
  def configReader: ConfigReader[Config]
  /** The classpath resource holding the plugin's defaults (HOCON, keys relative to its block). Its
    * own name per plugin — never `reference.conf`, which every jar would claim. */
  def defaultsResource: String = s"META-INF/histmersion/plugins/${info.id}.conf"

/** A work to look for: whichever identifiers the caller has. */
final case class WorkKey(doi: Option[String], arxiv: Option[String], title: Option[String], year: Option[Int])

/** A downloaded file and where it came from. */
final case class FetchedFile(source: String, url: Option[String], bytes: Array[Byte], extension: String)

/** On what terms a fetcher obtains a file. The host decides from this whether a fetcher may run at
  * all (an `Unauthorised` one only when the deployment says so, and never on the legal-only path). */
enum Access:
  /** Open access: the provider publishes the file for anyone. */
  case Open
  /** The user's own subscription or account, inside the provider's terms. */
  case Subscription
  /** Distributed without the rights holder's authorisation. Never part of this repository. */
  case Unauthorised

/** One answer of a fetcher. `Miss` lets the chain go on; `Refused` (a bot challenge, a 401/403, a
  * terms limit) also does, and is reported as such — never worked around. */
enum Fetch:
  case Found(file: FetchedFile)
  case Miss(why: String)
  case Refused(why: String)

/** The host's outbound HTTP — the one policy (User-Agent without personal data, Retry-After, bot
  * challenges stop the request). Plugins use this, never their own client. */
trait PluginHttp:
  def get(url: String, headers: Map[String, String] = Map.empty): Either[String, Array[Byte]]

/** What the host lends a running plugin. */
trait HostServices:
  def http: PluginHttp
  def log(message: String): Unit

trait SourceFetcher:
  def access: Access
  def fetch(work: WorkKey): Fetch

/** The source-fetching kind. `C` is the plugin's config; its reader is derived at the subclass's
  * `extends` clause, so the provider class keeps the no-argument constructor ServiceLoader needs. */
abstract class SourceFetcherPlugin[C](using reader: ConfigReader[C]) extends Plugin:
  type Config = C
  final def configReader: ConfigReader[C] = reader
  def create(config: C, host: HostServices): Either[String, SourceFetcher]
