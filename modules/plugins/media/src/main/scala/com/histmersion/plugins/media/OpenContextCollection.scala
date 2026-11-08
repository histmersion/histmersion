package com.histmersion
package plugins.media

import com.histmersion.plugin.*

/** `opencontext`: Open Context's excavation media. One search by the place's name answers the records with their
  * licences, creators, subjects and spatial coverage; a record to store is completed from its media item
  * ([[OpenContext]]). A record is tied by a shared gazetteer id first, else by its context path's names.
  */
final class OpenContextCollection(conf: CollectionSettings, licences: LicenceReader)
    extends MuseumCollection(conf, MuseumCollection.Provider("Open Context"), MuseumCollection.Key("media.open-context")):
  val title = MediaCollection.TitleResult("Open Context media")
  override val description = MediaCollection.DescriptionResult(
    "Open Context's excavation media (photographs, drawings), each record under its own Creative Commons licence " +
      "(read from the search record and again from the media item); tied to a place on the map by a shared " +
      "Pleiades/GeoNames/Wikidata id, else by its site's name in its context path, and its dates; records flagged " +
      "as related to human remains are not shown")
  val role = MediaCollection.RoleResult(MediaCollection.Object)
  override def matchRule = MediaMatchRule.PlaceIds(Set(MediaMatchRule.PlaceIds.Deeper(OpenContext.Context.value)))

  def search(place: MediaPlace, http: Plugin.Http): Either[MediaCollection.SearchResultPart1, List[Either[MediaCollection.SearchResultPart2Part1, MediaItem]]] =
    http
      .askJson(OpenContext.searchUrl(conf.apiUrl, place.label, conf.candidates))
      .left
      .map(e => MediaCollection.SearchResultPart1(e))
      .flatMap(j =>
        OpenContext
          .records(j, licences)
          .left
          .map(p => MediaCollection.SearchResultPart1(p.value))
          .map(_.map(_.left.map(p => MediaCollection.SearchResultPart2Part1(p.value))))
      )

  override def complete(item: MediaItem, http: Plugin.Http): Either[MediaCollection.CompleteResultPart1, MediaItem] =
    http
      .askJson(OpenContext.itemUrl(conf.apiUrl, OpenContext.Uuid(item.key.value)))
      .left
      .map(e => MediaCollection.CompleteResultPart1(e))
      .flatMap(j => OpenContext.item(j, item, licences).left.map(p => MediaCollection.CompleteResultPart1(p.value)))
