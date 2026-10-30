package com.histmersion.web.setup

/** What the setup downloads when it is finished — nothing before. */
enum SetupDownload(val label: String):
  case Map(area: String) extends SetupDownload("The background map")
  case Boundaries extends SetupDownload("Historical boundaries, regions and named periods")
  case Events extends SetupDownload("Volcanic eruptions, earthquakes and tsunamis")
  case Pictures extends SetupDownload("Pictures of places from Wikidata and Wikimedia Commons")
  case MorePlaces extends SetupDownload("More place names (GeoNames and Wikidata)")
