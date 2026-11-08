package com.histmersion
package plugins.media

import com.histmersion.plugin.{MediaCollection, MediaItem}
import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

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
  def placeRoles: List[(WikidataSettings.Property, MediaItem.Role)] =
    WikidataSettings.roles(WikidataSettings.Spec(placeProperties.value))
  def eventRoles: List[(WikidataSettings.Property, MediaItem.Role)] =
    WikidataSettings.roles(WikidataSettings.Spec(eventProperties.value))

object WikidataSettings:
  type Spec = Spec.T
  object Spec extends Newtype[String, Pure]

  /** A Wikidata property id (`P18`). */
  type Property = Property.T
  object Property extends Newtype[String, Pure]

  type ApiUrl = ApiUrl.T
  object ApiUrl extends Newtype[String, Pure]
  type Batch = Batch.T
  object Batch extends Newtype[Int, Pure]
  type Maxlag = Maxlag.T
  object Maxlag extends Newtype[Int, Pure]
  type PlaceProperties = PlaceProperties.T
  object PlaceProperties extends Newtype[String, Pure]
  type EventProperties = EventProperties.T
  object EventProperties extends Newtype[String, Pure]

  /** The roles a picture of a place or an event may play. */
  val PictureRoles: List[MediaItem.Role] = List(
    MediaCollection.Image,
    MediaCollection.AerialView,
    MediaCollection.Plan,
    MediaCollection.LocatorMap,
    MediaCollection.RouteMap
  ).map(MediaItem.Role(_))

  /** Pure: `P18:image,P242:locator-map` as (property, role), in order, one per property; an entry that is not a
    * property and a picture role is dropped.
    */
  def roles(spec: Spec): List[(Property, MediaItem.Role)] =
    spec.value
      .split(",")
      .toList
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap { e =>
        e.split(":", 2).map(_.trim) match
          case Array(p, r) if p.matches("P[1-9][0-9]*") && PictureRoles.contains(MediaItem.Role(r)) =>
            Some(Property(p) -> MediaItem.Role(r))
          case _ => None
      }
      .distinctBy(_._1)
