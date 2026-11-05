package com.histmersion.plugin.testkit

import com.histmersion.plugin.*

/** One place as the server would store it: the source's place, and the provenance line stored with it (its own, else
  * the dataset's credit).
  */
final case class AcceptedPlace(place: GazetteerPlace, source: Option[AcceptedPlace.Source])

object AcceptedPlace:
  type Source = Source.T
  object Source extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
