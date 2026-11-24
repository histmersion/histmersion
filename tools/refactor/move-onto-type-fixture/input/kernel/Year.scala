package fixture
package kernel

type Year = Year.T

object Year:
  opaque type T = Int
  def apply(raw: Int): T = raw
  extension (year: T) def value: Int = year
