package com.histmersion.server.media

import com.histmersion.pluginhost.{HostOwned, HttpTransport}

import com.histmersion.domain.{MediaLicence, MediaMatch, MediaRecord, MediaRights as CoreRights}
import com.histmersion.plugin.*
import com.histmersion.server.{ConverterClient, Deps}

import com.histmersion.plugin.PluginConfig.given

/** A media collection that is Histmersion's OWN code, registered through the plugin host like any other
  * (docs/research-plugins.md §6, D2) but run by the host's own import: the Wikidata/Commons runs and the museum, map
  * and excavation collections with their `media.<provider>.*` settings, unchanged. The SPI's search is never called for
  * it.
  */
trait HostMediaCollection extends MediaCollection, HostOwned:
  /** The name `media import` is asked for (`met`). */
  def name: String

  /** What it asks about: `places`, `events`, `places and events`. */
  def over: String

  /** The import, built over this server's config — or why it cannot run (`media.<provider>.enabled`). */
  def importer(deps: Deps, http: HttpTransport, converter: ConverterClient): Either[String, MediaImporter]
  def search(place: MediaPlace, http: PluginHttp): Either[String, List[Either[String, MediaItem]]] =
    Left(s"$name is Histmersion's own import; it is run by the host, not searched through the SPI")
