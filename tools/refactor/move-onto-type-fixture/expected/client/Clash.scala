package fixture.client

import fixture.api.Year
import fixture.kernel.Year.SignedYear

object Clash:
  val year: Year = Year(1)

  val signed: SignedYear = SignedYear(2L)

  object Bounds:
    export fixture.kernel.Year.{SignedYear as From, SignedYear as To}
