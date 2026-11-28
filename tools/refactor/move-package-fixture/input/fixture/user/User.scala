package fixture
package user

import fixture.old.Thing

object User:
  val one: Thing = Thing.of(1)
  val two: old.Thing = old.Thing.of(2)
  val three: fixture.old.Thing = fixture.old.Thing.of(3)
