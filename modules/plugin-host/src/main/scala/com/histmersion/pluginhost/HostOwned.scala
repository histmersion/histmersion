package com.histmersion.pluginhost

/** A contribution that is Histmersion's OWN code registered through the plugin host (a built-in media
  * collection, event catalogue or AI provider): it may take a reserved name, because it is the one the
  * name is reserved for. */
trait HostOwned

/** The names a plugin's contribution may not take, because Histmersion's own providers answer to them:
  * a plugin's pictures, events or models would be taken for theirs. The server's built-in registries
  * define the same sets; a server suite fails when the two disagree. */
object ReservedNames:
  import com.histmersion.api.{Agents, MediaProvider}
  import com.histmersion.domain.CatalogProvider

  /** The built-in media imports (`media import <name>`) and their aliases, and every provider pictures
    * are stored under. */
  val mediaCollections: Set[String] =
    Set("wikidata", "depicts", "met", "cleveland", "aic", "loc", "pleiades", "opencontext", "smithsonian", "europeana",
      "commons", "depictions", "cma", "artic", "oc", "si", "smithsonian-open-access") ++
      MediaProvider.all ++ Set(MediaProvider.CommonsDepicts, MediaProvider.Pleiades)

  /** The built-in event catalogues and their aliases. */
  val eventCatalogues: Set[String] = CatalogProvider.all.toSet ++ Set("smithsonian", "volcano", "ncei", "wd")

  /** The built-in AI providers. */
  val aiProviders: Set[String] = Agents.ProviderKind.values.map(_.wire).toSet

/** Where host code writes a log line: scribe on the server, a buffer in a test. */
trait HostLog:
  def info(line: String): Unit
  def warn(line: String): Unit

object HostLog:
  val scribe: HostLog = new HostLog:
    def info(line: String): Unit = _root_.scribe.info(line)
    def warn(line: String): Unit = _root_.scribe.warn(line)
