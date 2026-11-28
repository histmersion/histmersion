package fixture
package kernel

import scala.util.Try

object DeepTime:
  type SignedYear = SignedYear.T
  object SignedYear:
    opaque type T = Long
    def apply(raw: Long): T = raw
    extension (year: T) def value: Long = year

  def year(y: SignedYear): Option[Year] = yearOf(y.value)

  private def yearOf(y: Long): Option[Year] = Try(Math.toIntExact(y)).toOption.map(Year(_))

  def span(a: SignedYear, b: SignedYear): Option[(Year, Year)] =
    for
      start <- yearOf(a.value.min(b.value))
      end <- yearOf(a.value.max(b.value))
    yield (start, end)

  def later(y: Year, by: Int): Year = Year(y.value + by)
