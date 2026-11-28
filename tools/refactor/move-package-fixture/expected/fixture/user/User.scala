package fixture
package user

import fixture.feature.place.Thing

object User:
  val one: Thing = Thing.of(1)
  val two: feature.place.Thing = feature.place.Thing.of(2)
  val three: fixture.feature.place.Thing = fixture.feature.place.Thing.of(3)
