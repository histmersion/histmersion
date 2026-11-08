package com.histmersion
package plugin.testkit

import com.histmersion.plugin.*
import com.histmersion.api.catalogue.Place

/** One place as the server would store it: the source's place, and the provenance line stored with it (its own, else
  * the dataset's credit).
  */
final case class AcceptedPlace(place: GazetteerPlace, source: Option[Place.Source])
