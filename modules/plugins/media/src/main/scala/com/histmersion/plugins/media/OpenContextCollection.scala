package com.histmersion.plugins.media

import com.histmersion.plugin.*

/** `opencontext`: Open Context's excavation media. One search by the place's name answers the records with their
  * licences, creators, subjects and spatial coverage; a record to store is completed from its media item
  * ([[OpenContext]]). A record is tied by a shared gazetteer id first, else by its context path's names.
  */
final class OpenContextCollection(conf: CollectionSettings, licences: LicenceReader)
    extends MuseumCollection(conf, "Open Context", "media.open-context"):
  val title = "Open Context media"
  override val description =
    "Open Context's excavation media (photographs, drawings), each record under its own Creative Commons licence " +
      "(read from the search record and again from the media item); tied to a place on the map by a shared " +
      "Pleiades/GeoNames/Wikidata id, else by its site's name in its context path, and its dates; records flagged " +
      "as related to human remains are not shown"
  val role = MediaCollection.Object
  override def matchRule = MediaMatchRule.PlaceIds(Set(OpenContext.Context))

  def search(place: MediaPlace, http: Plugin.Http): Either[String, List[Either[String, MediaItem]]] =
    http
      .askJson(OpenContext.searchUrl(conf.apiUrl, place.label, conf.candidates))
      .flatMap(OpenContext.records(_, licences))

  override def complete(item: MediaItem, http: Plugin.Http): Either[String, MediaItem] =
    http.askJson(OpenContext.itemUrl(conf.apiUrl, item.key)).flatMap(OpenContext.item(_, item, licences))
