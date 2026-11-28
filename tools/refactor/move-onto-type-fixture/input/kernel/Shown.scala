package fixture.kernel

object Shown:
  def apply(year: Year): String = if year.value < 0 then s"${-year.value} BCE" else s"${year.value} CE"

  def apply(year: Option[Year]): String = year.fold("?")(apply)
