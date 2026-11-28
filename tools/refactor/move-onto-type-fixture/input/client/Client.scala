package fixture.client

import fixture.api.Lint
import fixture.api.Lint.Finding
import fixture.automation.ApprovalModes
import fixture.kernel.DeepTime
import fixture.kernel.DeepTime.*
import fixture.kernel.{Shown, Year}
import fixture.library.{Citekey, Citekeys}

object Client:
  val mode = ApprovalModes.configured("auto")

  val period = DeepTime.span(DeepTime.SignedYear(-1400L), DeepTime.SignedYear(-1200L))

  val first: Option[Year] = year(SignedYear(3L))

  def signed(raw: Long): DeepTime.SignedYear = DeepTime.SignedYear(raw)

  val later: Year = DeepTime.later(Year(1), 2)

  val shifted: Year = DeepTime.later(DeepTime.later(Year(1), -1), 2)

  val ck = Citekey("knapp2016")

  val renamed = Citekeys.renameTo(ck, " page1959 ")

  val chained = Citekeys.renameTo(Citekeys.renameTo(ck, "a").toOption.get, "b")

  val ok = Citekeys.valid("x")

  val findings = List(Finding("rule", 3), Lint.Finding("other", 1))

  val first3 = Lint.worst(findings)

  val shown = Shown(Year(-3300)) + Shown(Option(Year(1)))
