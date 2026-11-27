package fixture
package kernel

import scala.util.Try

type Year = Year.T

object Year:
  opaque type T = Int
  def apply(raw: Int): T = raw
  extension (year: T) def value: Int = year

  def show(year: Year): String = if year.value < 0 then s"${-year.value} BCE" else s"${year.value} CE"

  def show(year: Option[Year]): String = year.fold("?")(show)

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

  extension (y: Year) def later(by: Int): Year = Year(y.value + by)
