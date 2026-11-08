package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** Pictures that depict an event (`media.depicts`): Commons files whose structured data says `P180 depicts` the event's
  * item, and the images of the works its item lists as `P1299 depicted by`.
  */
final case class MediaDepictsConfig(perEvent: MediaDepictsConfig.PerEvent, searchLimit: MediaDepictsConfig.SearchLimit, depictedBy: MediaDepictsConfig.DepictedBy, licences: MediaDepictsConfig.Licences)
    derives ConfigReader

object MediaDepictsConfig:
  type PerEvent = PerEvent.T
  object PerEvent extends Newtype[Int, Pure]
  type SearchLimit = SearchLimit.T
  object SearchLimit extends Newtype[Int, Pure]
  type DepictedBy = DepictedBy.T
  object DepictedBy extends Newtype[Boolean, Pure]
  type Licences = Licences.T
  object Licences extends Newtype[String, Pure]
