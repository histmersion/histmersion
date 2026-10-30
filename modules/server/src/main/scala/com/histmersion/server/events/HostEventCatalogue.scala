package com.histmersion.server.events

import com.histmersion.pluginhost.HostOwned

import com.histmersion.api.CatalogProviderInfo
import com.histmersion.domain.CatalogProvider
import com.histmersion.plugin.*

/** An event catalogue that is Histmersion's OWN code (GVP, NOAA, Wikidata), registered through the plugin host like any
  * other (docs/research-plugins.md §6, D2) but read by the host's own import with its `events.<catalogue>.*` settings,
  * unchanged. The SPI's `parts`/`read` are never called for it.
  */
final case class HostEventCatalogue(plugin: String, name: String) extends EventCatalogue, HostOwned:
  private def info: Option[CatalogProviderInfo] = CatalogProvider.info(name)
  def title: String = info.fold(name)(_.title)
  def licence: Licence = Licence(info.fold("")(_.licence))
  def citation: String = info.fold("")(_.citation)
  def home: String = info.fold("")(_.home)
  def parts(query: CatalogueQuery): List[String] = Nil
  def read(part: String, query: CatalogueQuery, http: PluginHttp): Either[String, CataloguePage] =
    Left(s"$name is Histmersion's own import; it is read by the host, not through the SPI")
