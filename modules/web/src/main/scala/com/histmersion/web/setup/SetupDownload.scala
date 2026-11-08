package com.histmersion.web.setup

/** What the setup downloads when it is finished — nothing before. */
enum SetupDownload(val label: SetupDownload.Label):
  case Map(area: SetupDownload.Map.Area) extends SetupDownload(com.histmersion.web.setup.SetupDownload.Label("The background map"))
  case Boundaries extends SetupDownload(com.histmersion.web.setup.SetupDownload.Label("Historical boundaries, regions and named periods"))
  case Events extends SetupDownload(com.histmersion.web.setup.SetupDownload.Label("Volcanic eruptions, earthquakes and tsunamis"))
  case Pictures extends SetupDownload(com.histmersion.web.setup.SetupDownload.Label("Pictures of places from Wikidata and Wikimedia Commons"))
  case MorePlaces extends SetupDownload(com.histmersion.web.setup.SetupDownload.Label("More place names (GeoNames and Wikidata)"))

object SetupDownload:
  object Map:
    export com.histmersion.web.setup.SetupChoices.Area

  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
