package com.histmersion.domain

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.domain.AgentVerify.*
import com.histmersion.api.catalogue.Period
import com.histmersion.api.library.Source

class AgentVerifySuite extends munit.FunSuite:

  private val body =
    """# Results
      |
      |The Uluburun ship carried ten tons of cop-
      |per and one ton of tin, “a ratio typical of bronze” according to
      |the excavators. The ﬁnds date to about 1320 BCE.
      |""".stripMargin

  test("quote: verbatim modulo hyphenation, ligatures, Unicode quotes and whitespace"):
    assertEquals(findQuote(com.histmersion.domain.AgentProposals.Proposal.Quote("ten tons of copper and one ton of tin"), body, 0.9), QuoteMatch.Verbatim)
    assertEquals(findQuote(com.histmersion.domain.AgentProposals.Proposal.Quote("\"a ratio typical of bronze\" according to the excavators"), body, 0.9), QuoteMatch.Verbatim)
    assertEquals(findQuote(com.histmersion.domain.AgentProposals.Proposal.Quote("The finds date to about 1320 BCE."), body, 0.9), QuoteMatch.Verbatim)

  test("quote: OCR noise (a slipped letter, a dropped word) is a fuzzy match with its ratio"):
    val noisy = "The Uluburun shlp carried ten tons of copper and one ton of qxzv tin"
    findQuote(noisy, body, 0.85) match
      case QuoteMatch.Fuzzy(r) => assert(r >= 0.85 && r < 1.0, r)
      case other               => fail(s"expected Fuzzy, got $other")

  test("quote: an invented or paraphrased quote is missing"):
    assert(!findQuote(com.histmersion.domain.AgentProposals.Proposal.Quote("The ship sank off the coast of Cyprus after a storm in winter"), body, 0.9).accepted)
    assert(!findQuote(com.histmersion.domain.AgentProposals.Proposal.Quote("carried copper and tin from Cornwall to Egypt by sea"), body, 0.9).accepted)

  test("quote: a short quote must be verbatim — three words match anywhere by chance"):
    assert(!findQuote(com.histmersion.domain.AgentProposals.Proposal.Quote("ten tins of"), body, 0.5).accepted)
    assertEquals(findQuote(com.histmersion.domain.AgentProposals.Proposal.Quote("ten tons of"), body, 0.5), QuoteMatch.Verbatim)

  test("similarWord: one edit at four letters or more, never on short words"):
    assert(similarWord("mycenaean", "mycenacan"))
    assert(!similarWord("tbe", "the"))
    assert(similarWord("bronze", "bronz"))
    assert(!similarWord("bronze", "brass"))

  test("BCE/CE → signed years; there is no year 0"):
    assertEquals(toSignedYear(com.histmersion.domain.AgentVerify.ToSignedYearValue(3300), YearUnit.BCE), Right(-3300L))
    assertEquals(toSignedYear(com.histmersion.domain.AgentVerify.ToSignedYearValue(1), YearUnit.CE), Right(1L))
    assert(toSignedYear(com.histmersion.domain.AgentVerify.ToSignedYearValue(0), YearUnit.CE).isLeft)
    assert(toSignedYear(com.histmersion.domain.AgentVerify.ToSignedYearValue(0), YearUnit.BCE).isLeft)

  test("calBP converts as astronomical 1950 − BP, mapped to historical reckoning"):
    assertEquals(toSignedYear(com.histmersion.domain.AgentVerify.ToSignedYearValue(1949), YearUnit.CalBP), Right(1L)) // astronomical 1 = 1 CE
    assertEquals(toSignedYear(com.histmersion.domain.AgentVerify.ToSignedYearValue(1950), YearUnit.CalBP), Right(-1L)) // astronomical 0 = 1 BCE
    assertEquals(toSignedYear(com.histmersion.domain.AgentVerify.ToSignedYearValue(3950), YearUnit.CalBP), Right(-2001L)) // astronomical -2000 = 2001 BCE
    assertEquals(toSignedYear(com.histmersion.domain.AgentVerify.ToSignedYearValue(0), YearUnit.CalBP), Right(1950L))

  test("uncalibrated BP is refused, never guessed into a calendar year"):
    assert(toSignedYear(com.histmersion.domain.AgentVerify.ToSignedYearValue(3270), YearUnit.BP).left.exists(_.contains("uncalibrated")))

  test("YearUnit: the spellings sources use"):
    assertEquals(YearUnit.parse(com.histmersion.domain.AgentVerify.YearUnit.Wire("cal BP")), Some(YearUnit.CalBP))
    assertEquals(YearUnit.parse(com.histmersion.domain.AgentVerify.YearUnit.Wire("BC")), Some(YearUnit.BCE))
    assertEquals(YearUnit.parse(com.histmersion.domain.AgentVerify.YearUnit.Wire("AD")), Some(YearUnit.CE))
    assertEquals(YearUnit.parse(com.histmersion.domain.AgentVerify.YearUnit.Wire("14C BP")), Some(YearUnit.BP))
    assertEquals(YearUnit.parse(com.histmersion.domain.AgentVerify.YearUnit.Wire("Ma")), None)

  test("period sanity: order, year 0, bound, and the source's own span"):
    assertEquals(periodProblems(com.histmersion.domain.AgentVerify.PeriodStart(-1600), com.histmersion.domain.AgentVerify.PeriodEnd(-1500), com.histmersion.domain.AgentVerify.MaxAbsYear(3000000)), Nil)
    assert(periodProblems(com.histmersion.domain.AgentVerify.PeriodStart(-1500), com.histmersion.domain.AgentVerify.PeriodEnd(-1600), com.histmersion.domain.AgentVerify.MaxAbsYear(3000000)).exists(_.contains("after it ends")))
    assert(periodProblems(com.histmersion.domain.AgentVerify.PeriodStart(0), com.histmersion.domain.AgentVerify.PeriodEnd(10), com.histmersion.domain.AgentVerify.MaxAbsYear(3000000)).exists(_.contains("year 0")))
    assert(periodProblems(com.histmersion.domain.AgentVerify.PeriodStart(-4000000), com.histmersion.domain.AgentVerify.PeriodEnd(-1), com.histmersion.domain.AgentVerify.MaxAbsYear(3000000)).exists(_.contains("beyond")))
    assert(periodProblems(com.histmersion.domain.AgentVerify.PeriodStart(-3000), com.histmersion.domain.AgentVerify.PeriodEnd(-2900), com.histmersion.domain.AgentVerify.MaxAbsYear(3000000), Some(Period(com.histmersion.api.catalogue.Year(-1700), com.histmersion.api.catalogue.Year(-1200)))).exists(_.contains("outside")))

  test("status vocabulary: a typo is rejected, not turned into unassessed"):
    assertEquals(status(com.histmersion.domain.library.Source.Status.Wire("Current")), Right(Source.Status.Current))
    assert(status(com.histmersion.domain.library.Source.Status.Wire("debunk")).isLeft)

  test("links: dangling targets and escaped brackets are problems"):
    val text = "See [[haak2015_massive]] and [[ie-migrations|the article]] and [[nope2020_x]]. Also \\[\\[x\\]\\]."
    val ps = linkProblems(text, Set("haak2015_massive"), Set("ie-migrations"))
    assertEquals(ps.size, 2, ps)
    assert(ps.exists(_.contains("[[nope2020_x]]")))
    assert(ps.exists(_.contains("escaped")))
    assertEquals(wikilinks(com.histmersion.domain.AgentVerify.Text("[[a]] [[b|B]] [[a]]")), List("a", "b"))

  test("titles compare without case or punctuation"):
    assert(sameTitle(com.histmersion.domain.Title("The Ahhiyawa Texts"), com.histmersion.domain.Title("the ahhiyawa texts.")))
    assert(!sameTitle(com.histmersion.domain.Title("The Ahhiyawa Texts"), com.histmersion.domain.Title("Ahhiyawa")))
