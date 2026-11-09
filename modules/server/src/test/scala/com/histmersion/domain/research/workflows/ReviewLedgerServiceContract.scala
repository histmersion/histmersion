package com.histmersion
package domain.research.workflows

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.Caller
import com.histmersion.domain.ReviewArticle.{Finding, Reviewer, Severity}
import com.histmersion.domain.ReviewLedger.{Entry, Ledger, Status}
import com.histmersion.domain.research.{ResearchStores, ResearchStoreSuite, Slug}
import com.histmersion.api.research.Article

/** What the review ledgers answer, the same on the database and in memory: a ledger written and read back, none for an
  * article never reviewed, refused for an article that does not exist, passed once no MAJOR is open, following its
  * article's rename and gone with its delete.
  */
abstract class ReviewLedgerServiceContract extends ResearchStoreSuite:

  private val Me = Caller.Local

  private def article(slug: Slug): Article =
    Article(
      Article.Slug(slug.value),
      Article.Title("Walls"),
      Article.KindValue("concept"),
      Article.Body("## Overview\n\nThe walls.\n"),
      None,
      Nil,
      Nil,
      None,
      None,
      None,
      Map.empty
    )

  private def finding(id: Finding.Id, severity: Severity) =
    Finding(
      id,
      Reviewer.Rules,
      severity,
      Finding.Kind("synth"),
      Finding.Where(s"where $id"),
      Finding.Quote(s"quote $id"),
      Finding.Problem(s"problem $id"),
      Finding.Fix("fix")
    )

  private val Walls = Slug("zr_walls")
  private val Gate = Slug("zr_gate")
  private val R1 = Finding.Id("R1")
  private val R2 = Finding.Id("R2")

  stest("a ledger written and read back; none for an article never reviewed; refused for one that does not exist") {
    s =>
      val _ = s.articles.put(article(Walls), Me)
      assertEquals(s.ledgers.get(Walls), Right(None))
      val l = Ledger(
        Walls,
        List(Entry(finding(R1, Severity.Major), Entry.Run.assume("run-1"), Entry.Round(1))),
        task = Some(Ledger.Task(7))
      )
      s.ledgers.put(l)
      assertEquals(s.ledgers.get(Walls), Right(Some(l)))
      val again = l.modify(_.task).setTo(None)
      s.ledgers.put(again)
      assertEquals(s.ledgers.get(Walls), Right(Some(again)))
      intercept[Exception](s.ledgers.put(Ledger(Ledger.Slug("zr_nothing"))))
      assertEquals(s.ledgers.get(Slug("zr_nothing")), Right(None))
  }

  stest("an article passed its review once its ledger holds no open MAJOR") { s =>
    val _ = s.articles.put(article(Walls), Me)
    val _ = s.articles.put(article(Gate), Me)
    s.ledgers.put(Ledger(Walls, List(Entry(finding(R1, Severity.Major), Entry.Run.assume("run-1"), Entry.Round(1)))))
    s.ledgers.put(
      Ledger(
        Gate,
        List(
          Entry(finding(R1, Severity.Major), Entry.Run.assume("run-1"), Entry.Round(1), status = Status.Fixed),
          Entry(finding(R2, Severity.Minor), Entry.Run.assume("run-1"), Entry.Round(1))
        )
      )
    )
    assertEquals(s.ledgers.passedReview(ReviewLedgerService.ReviewId("review")), Set(Gate))
  }

  stest("a ledger follows its article's rename and goes with its delete") { s =>
    val _ = s.articles.put(article(Walls), Me)
    val l = Ledger(Walls, List(Entry(finding(R1, Severity.Major), Entry.Run.assume("run-1"), Entry.Round(1))))
    s.ledgers.put(l)
    assert(s.articles.rename(Walls, Slug("zr_city_walls"), Me).isRight)
    assertEquals(s.ledgers.get(Slug("zr_city_walls")), Right(Some(l)))
    assertEquals(s.ledgers.get(Walls), Right(None))
    assert(s.articles.delete(Slug("zr_city_walls"), Me))
    assertEquals(s.ledgers.get(Slug("zr_city_walls")), Right(None))
  }

class ReviewLedgerServiceInMemorySuite extends ReviewLedgerServiceContract with ResearchStoreSuite.InMemory
class ReviewLedgerServiceDbSuite extends ReviewLedgerServiceContract with ResearchStoreSuite.OnDatabase
