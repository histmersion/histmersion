package com.histmersion.domain.research.workflows

import com.histmersion.domain.Caller
import com.histmersion.domain.ReviewArticle.{Finding, Reviewer, Severity}
import com.histmersion.domain.ReviewLedger.{Entry, Ledger, Status}
import com.histmersion.domain.research.{ResearchStores, ResearchStoreSuite}
import com.histmersion.api.research.Article

/** What the review ledgers answer, the same on the database and in memory: a ledger written and read back, none for an
  * article never reviewed, refused for an article that does not exist, passed once no MAJOR is open, following its
  * article's rename and gone with its delete.
  */
abstract class ReviewLedgerServiceContract extends ResearchStoreSuite:

  private val Me = Caller.Local

  private def article(slug: String): Article =
    Article(slug, "Walls", "concept", "## Overview\n\nThe walls.\n", None, Nil, Nil, None, None, None, Map.empty)

  private def finding(id: String, severity: Severity) =
    Finding(id, Reviewer.Rules, severity, "synth", s"where $id", s"quote $id", s"problem $id", "fix")

  private val Walls = "zr_walls"
  private val Gate = "zr_gate"

  stest("a ledger written and read back; none for an article never reviewed; refused for one that does not exist") {
    s =>
      val _ = s.articles.put(article(Walls), Me)
      assertEquals(s.ledgers.get(Walls), Right(None))
      val l = Ledger(Walls, List(Entry(finding("R1", Severity.Major), "run-1", 1)), task = Some(7))
      s.ledgers.put(l)
      assertEquals(s.ledgers.get(Walls), Right(Some(l)))
      val again = l.copy(task = None)
      s.ledgers.put(again)
      assertEquals(s.ledgers.get(Walls), Right(Some(again)))
      intercept[Exception](s.ledgers.put(Ledger("zr_nothing")))
      assertEquals(s.ledgers.get("zr_nothing"), Right(None))
  }

  stest("an article passed its review once its ledger holds no open MAJOR") { s =>
    val _ = s.articles.put(article(Walls), Me)
    val _ = s.articles.put(article(Gate), Me)
    s.ledgers.put(Ledger(Walls, List(Entry(finding("R1", Severity.Major), "run-1", 1))))
    s.ledgers.put(
      Ledger(
        Gate,
        List(
          Entry(finding("R1", Severity.Major), "run-1", 1, status = Status.Fixed),
          Entry(finding("R2", Severity.Minor), "run-1", 1)
        )
      )
    )
    assertEquals(s.ledgers.passedReview("review"), Set(Gate))
  }

  stest("a ledger follows its article's rename and goes with its delete") { s =>
    val _ = s.articles.put(article(Walls), Me)
    val l = Ledger(Walls, List(Entry(finding("R1", Severity.Major), "run-1", 1)))
    s.ledgers.put(l)
    assert(s.articles.rename(Walls, "zr_city_walls", Me).isRight)
    assertEquals(s.ledgers.get("zr_city_walls"), Right(Some(l)))
    assertEquals(s.ledgers.get(Walls), Right(None))
    assert(s.articles.delete("zr_city_walls", Me))
    assertEquals(s.ledgers.get("zr_city_walls"), Right(None))
  }

class ReviewLedgerServiceInMemorySuite extends ReviewLedgerServiceContract with ResearchStoreSuite.InMemory
class ReviewLedgerServiceDbSuite extends ReviewLedgerServiceContract with ResearchStoreSuite.OnDatabase
