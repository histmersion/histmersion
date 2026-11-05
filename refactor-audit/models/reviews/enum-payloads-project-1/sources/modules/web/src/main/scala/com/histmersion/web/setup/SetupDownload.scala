package com.histmersion.web.setup

/** What the setup downloads when it is finished — nothing before. */
enum SetupDownload(val label: SetupDownload.Label):
  case Map(area: Map.Area) extends SetupDownload("The background map")
  case Boundaries extends SetupDownload("Historical boundaries, regions and named periods")
  case Events extends SetupDownload("Volcanic eruptions, earthquakes and tsunamis")
  case Pictures extends SetupDownload("Pictures of places from Wikidata and Wikimedia Commons")
  case MorePlaces extends SetupDownload("More place names (GeoNames and Wikidata)")

object SetupDownload:
  object Map:
    type Area = Area.T
    object Area extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
