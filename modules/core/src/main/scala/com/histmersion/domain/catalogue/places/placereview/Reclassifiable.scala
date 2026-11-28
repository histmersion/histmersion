package com.histmersion
package domain.catalogue.places.placereview

import com.histmersion.domain.catalogue.Place

final case class Reclassifiable(id: Evidence.Id, label: Evidence.Label, kind: Place.Kind.Wire)
