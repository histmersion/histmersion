package com.histmersion
package web.setup

import io.github.iltotore.iron.constraint.any.Pure

/** What the setup downloads when it is finished — nothing before. */
enum SetupDownload(val label: SetupDownload.Label) derives CanEqual:
  case Map(area: SetupDownload.Map.Area) extends SetupDownload(SetupDownload.Label("The background map"))
  case Boundaries extends SetupDownload(SetupDownload.Label("Historical boundaries, regions and named periods"))
  case Events extends SetupDownload(SetupDownload.Label("Volcanic eruptions, earthquakes and tsunamis"))
  case Pictures extends SetupDownload(SetupDownload.Label("Pictures of places from Wikidata and Wikimedia Commons"))
  case MorePlaces extends SetupDownload(SetupDownload.Label("More place names (GeoNames and Wikidata)"))

object SetupDownload:
  object Map:
    export SetupChoices.Area

  type Label = Label.T
  object Label extends Newtype[String, Pure]
