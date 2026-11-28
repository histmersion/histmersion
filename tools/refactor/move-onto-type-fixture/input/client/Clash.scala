package fixture.client

import fixture.api.Year
import fixture.kernel.DeepTime

object Clash:
  val year: Year = Year(1)

  val signed: DeepTime.SignedYear = DeepTime.SignedYear(2L)

  object Bounds:
    export DeepTime.{SignedYear as From, SignedYear as To}
