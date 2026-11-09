package com.histmersion
package domain

import com.histmersion.api.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.domain.AgentVerify.*
import com.histmersion.api.catalogue.Period
import com.histmersion.api.library.Source
import com.histmersion.api.catalogue.Year

class AgentVerifySuite extends munit.FunSuite:

  private val body = SourceBody(
    """# Results
      |
      |The Uluburun ship carried ten tons of cop-
      |per and one ton of tin, “a ratio typical of bronze” according to
      |the excavators. The ﬁnds date to about 1320 BCE.
      |""".stripMargin
  )

  test("quote: verbatim modulo hyphenation, ligatures, Unicode quotes and whitespace"):
    assertEquals(
      findQuote(AgentProposals.Proposal.Quote("ten tons of copper and one ton of tin"), body, MinRatio(0.9)),
      QuoteMatch.Verbatim
    )
    assertEquals(
      findQuote(
        AgentProposals.Proposal.Quote("\"a ratio typical of bronze\" according to the excavators"),
        body,
        MinRatio(0.9)
      ),
      QuoteMatch.Verbatim
    )
    assertEquals(
      findQuote(AgentProposals.Proposal.Quote("The finds date to about 1320 BCE."), body, MinRatio(0.9)),
      QuoteMatch.Verbatim
    )

  test("quote: OCR noise (a slipped letter, a dropped word) is a fuzzy match with its ratio"):
    val noisy = AgentProposals.Proposal.Quote("The Uluburun shlp carried ten tons of copper and one ton of qxzv tin")
    findQuote(noisy, body, MinRatio(0.85)) match
      case QuoteMatch.Fuzzy(r) => assert(r.value >= 0.85 && r.value < 1.0, r)
      case other               => fail(s"expected Fuzzy, got $other")

  test("quote: an invented or paraphrased quote is missing"):
    assert(
      !findQuote(
        AgentProposals.Proposal.Quote("The ship sank off the coast of Cyprus after a storm in winter"),
        body,
        MinRatio(0.9)
      ).accepted
    )
    assert(
      !findQuote(
        AgentProposals.Proposal.Quote("carried copper and tin from Cornwall to Egypt by sea"),
        body,
        MinRatio(0.9)
      ).accepted
    )

  test("quote: a short quote must be verbatim — three words match anywhere by chance"):
    assert(!findQuote(AgentProposals.Proposal.Quote("ten tins of"), body, MinRatio(0.5)).accepted)
    assertEquals(findQuote(AgentProposals.Proposal.Quote("ten tons of"), body, MinRatio(0.5)), QuoteMatch.Verbatim)

  test("similarWord: one edit at four letters or more, never on short words"):
    assert(similarWord("mycenaean", "mycenacan"))
    assert(!similarWord("tbe", "the"))
    assert(similarWord("bronze", "bronz"))
    assert(!similarWord("bronze", "brass"))

  test("BCE/CE → signed years; there is no year 0"):
    assertEquals(toSignedYear(ToSignedYearValue(3300), YearUnit.BCE), Right(-3300L))
    assertEquals(toSignedYear(ToSignedYearValue(1), YearUnit.CE), Right(1L))
    assert(toSignedYear(ToSignedYearValue(0), YearUnit.CE).isLeft)
    assert(toSignedYear(ToSignedYearValue(0), YearUnit.BCE).isLeft)

  test("calBP converts as astronomical 1950 − BP, mapped to historical reckoning"):
    assertEquals(toSignedYear(ToSignedYearValue(1949), YearUnit.CalBP), Right(1L)) // astronomical 1 = 1 CE
    assertEquals(toSignedYear(ToSignedYearValue(1950), YearUnit.CalBP), Right(-1L)) // astronomical 0 = 1 BCE
    assertEquals(toSignedYear(ToSignedYearValue(3950), YearUnit.CalBP), Right(-2001L)) // astronomical -2000 = 2001 BCE
    assertEquals(toSignedYear(ToSignedYearValue(0), YearUnit.CalBP), Right(1950L))

  test("uncalibrated BP is refused, never guessed into a calendar year"):
    assert(toSignedYear(ToSignedYearValue(3270), YearUnit.BP).left.exists(_.sentence.contains("uncalibrated")))

  test("YearUnit: the spellings sources use"):
    assertEquals(YearUnit.parse(YearUnit.Wire("cal BP")), Some(YearUnit.CalBP))
    assertEquals(YearUnit.parse(YearUnit.Wire("BC")), Some(YearUnit.BCE))
    assertEquals(YearUnit.parse(YearUnit.Wire("AD")), Some(YearUnit.CE))
    assertEquals(YearUnit.parse(YearUnit.Wire("14C BP")), Some(YearUnit.BP))
    assertEquals(YearUnit.parse(YearUnit.Wire("Ma")), None)

  test("period sanity: order, year 0, bound, and the source's own span"):
    assertEquals(periodProblems(PeriodStart(-1600), PeriodEnd(-1500), MaxAbsYear(3000000)), Nil)
    assert(
      periodProblems(PeriodStart(-1500), PeriodEnd(-1600), MaxAbsYear(3000000)).exists(
        _.sentence.contains("after it ends")
      )
    )
    assert(periodProblems(PeriodStart(0), PeriodEnd(10), MaxAbsYear(3000000)).exists(_.sentence.contains("year 0")))
    assert(
      periodProblems(PeriodStart(-4000000), PeriodEnd(-1), MaxAbsYear(3000000)).exists(_.sentence.contains("beyond"))
    )
    assert(
      periodProblems(PeriodStart(-3000), PeriodEnd(-2900), MaxAbsYear(3000000), Some(Period(Year(-1700), Year(-1200))))
        .exists(_.sentence.contains("outside"))
    )

  test("status vocabulary: a typo is rejected, not turned into unassessed"):
    assertEquals(status(com.histmersion.domain.library.Source.Status.Wire("Current")), Right(Source.Status.Current))
    assert(status(com.histmersion.domain.library.Source.Status.Wire("debunk")).isLeft)

  test("links: dangling targets and escaped brackets are problems"):
    val text = LinkProblemsText(
      "See [[haak2015_massive]] and [[ie-migrations|the article]] and [[nope2020_x]]. Also \\[\\[x\\]\\]."
    )
    val ps = linkProblems(text, _.value == "haak2015_massive", _.value == "ie-migrations")
    assertEquals(ps.size, 2, ps)
    assert(ps.exists(_.sentence.contains("[[nope2020_x]]")))
    assert(ps.exists(_.sentence.contains("escaped")))
    assertEquals(wikilinks(Text("[[a]] [[b|B]] [[a]]")), List("a", "b"))

  test("titles compare without case or punctuation"):
    assert(sameTitle(Title("The Ahhiyawa Texts"), Title("the ahhiyawa texts.")))
    assert(!sameTitle(Title("The Ahhiyawa Texts"), Title("Ahhiyawa")))
