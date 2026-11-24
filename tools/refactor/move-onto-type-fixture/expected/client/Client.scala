package fixture.client

import fixture.kernel.Year
import fixture.library.{Citekey, Citekeys}
import fixture.api.{Finding, worst}
import fixture.automation.Approval

object Client:
  val mode = Approval.Mode.configured("auto")

  val period = Year.span(Year.SignedYear(-1400L), Year.SignedYear(-1200L))

  val first: Option[Year] = Year.year(Year.SignedYear(3L))

  def signed(raw: Long): Year.SignedYear = Year.SignedYear(raw)

  val later: Year = Year(1).later(2)

  val shifted: Year = Year(1).later(-1).later(2)

  val ck = Citekey("knapp2016")

  val renamed = ck.renameTo(" page1959 ")

  val chained = ck.renameTo("a").toOption.get.renameTo("b")

  val ok = Citekeys.valid("x")

  val findings = List(Finding("rule", 3), Finding("other", 1))

  val first3 = worst(findings)

  val shown = Year.show(Year(-3300)) + Year.show(Option(Year(1)))
