package com.histmersion.plugins.media

import com.histmersion.plugin.MediaCollection
import hearth.kindlings.sconfigderivation.ConfigReader

/** `histmersion.media.wikidata`: the API, ids per call, `maxlag`, and the picture properties of a place and an event
  * with the role each plays (`P18:image,P242:locator-map`).
  */
final case class WikidataSettings(
    apiUrl: WikidataSettings.ApiUrl,
    batch: WikidataSettings.Batch,
    maxlag: WikidataSettings.Maxlag,
    placeProperties: WikidataSettings.PlaceProperties,
    eventProperties: WikidataSettings.EventProperties
) derives ConfigReader:
  def placeRoles: List[(com.histmersion.plugins.media.WikidataSettings.PlaceRolesResultPart1, com.histmersion.plugins.media.WikidataSettings.PlaceRolesResultPart2)] = WikidataSettings.roles(placeProperties)
  def eventRoles: List[(com.histmersion.plugins.media.WikidataSettings.EventRolesResultPart1, com.histmersion.plugins.media.WikidataSettings.EventRolesResultPart2)] = WikidataSettings.roles(eventProperties)

object WikidataSettings:
  /** The roles a picture of a place or an event may play. */
  type PlaceRolesResultPart1 = PlaceRolesResultPart1.T
  object PlaceRolesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceRolesResultPart2 = PlaceRolesResultPart2.T
  object PlaceRolesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventRolesResultPart1 = EventRolesResultPart1.T
  object EventRolesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventRolesResultPart2 = EventRolesResultPart2.T
  object EventRolesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PictureRoles = PictureRolesValue
  type PictureRolesValue = PictureRolesValue.T
  object PictureRolesValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RolesResultPart1 = RolesResultPart1.T
  object RolesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RolesResultPart2 = RolesResultPart2.T
  object RolesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RolesSpec = RolesSpec.T
  object RolesSpec extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ApiUrl = ApiUrl.T
  object ApiUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Batch = Batch.T
  object Batch extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Maxlag = Maxlag.T
  object Maxlag extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceProperties = PlaceProperties.T
  object PlaceProperties extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventProperties = EventProperties.T
  object EventProperties extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val PictureRoles: List[com.histmersion.plugins.media.WikidataSettings.PictureRoles] = List(
    MediaCollection.Image,
    MediaCollection.AerialView,
    MediaCollection.Plan,
    MediaCollection.LocatorMap,
    MediaCollection.RouteMap
  )

  /** Pure: `P18:image,P242:locator-map` as (property, role), in order, one per property; an entry that is not a
    * property and a picture role is dropped.
    */
  def roles(spec: com.histmersion.plugins.media.WikidataSettings.RolesSpec): List[(com.histmersion.plugins.media.WikidataSettings.RolesResultPart1, com.histmersion.plugins.media.WikidataSettings.RolesResultPart2)] =
    CollectionSettings
      .items(spec)
      .flatMap { e =>
        e.split(":", 2).map(_.trim) match
          case Array(p, r) if p.matches("P[1-9][0-9]*") && PictureRoles.contains(r) => Some(p -> r)
          case _                                                                    => None
      }
      .distinctBy(_._1)
