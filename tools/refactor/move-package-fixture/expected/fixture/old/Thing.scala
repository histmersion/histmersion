package fixture
package feature.place

import fixture.feature.place.Neighbour

final case class Thing(n: Int, near: Neighbour)

object Thing:
  def of(n: Int): Thing = Thing(n, Neighbour(n))
