package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** Pictures that depict an event (`media.depicts`): Commons files whose structured data says `P180 depicts` the event's
  * item, and the images of the works its item lists as `P1299 depicted by`.
  */
final case class MediaDepictsConfig(perEvent: MediaDepictsConfig.PerEvent, searchLimit: MediaDepictsConfig.SearchLimit, depictedBy: MediaDepictsConfig.DepictedBy, licences: MediaDepictsConfig.Licences)
    derives ConfigReader

object MediaDepictsConfig:
  type PerEvent = PerEvent.T
  object PerEvent extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchLimit = SearchLimit.T
  object SearchLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DepictedBy = DepictedBy.T
  object DepictedBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Licences = Licences.T
  object Licences extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
