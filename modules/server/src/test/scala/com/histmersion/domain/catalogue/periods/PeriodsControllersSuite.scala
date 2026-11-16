package com.histmersion
package domain.catalogue.periods
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.Caller
import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.util.data.maintenance.WriteGate

import java.util.concurrent.atomic.AtomicReference
import com.histmersion.api.catalogue.HistoricalPeriod

/** The periods' controllers over the in-memory service, no server and no database: a PeriodO dump sent and fetched (one
  * collection, under one node per authority), the bundled seed missing, an unknown source and dataset refused, one
  * period shown with its parent and parts, and every write refused while the maintenance lock is held.
  */
class PeriodsControllersSuite extends munit.FunSuite:
  private val dump = scala.io.Source.fromResource("periods/periodo_dump.json")(using scala.io.Codec.UTF8).mkString

  private def controllers(
      download: PeriodSources.PeriodoUrl => Either[Problem, PeriodImports.Document] = _ =>
        Right(PeriodImports.Document(dump)),
      seed: () => Either[Problem, PeriodImports.Document] = () =>
        Left(Problem.Unclassified(Legacy.Text("no period seed here"))),
      gate: WriteGate = WriteGate()
  ): PeriodsControllers =
    val periods = PeriodService.InMemory(AtomicReference(CatalogueState.empty))
    val imports =
      new PeriodImports(
        periods,
        () =>
          PeriodSources(
            PeriodSources.PeriodoUrl("https://periodo.test/dump"),
            List(PeriodSources.Collections("zzqauth"))
          ),
        seed,
        download
      )
    PeriodsControllers(periods, imports, gate)

  test("a sent dump is grouped under its authorities; a period shows its parent and its parts"):
    val c = controllers()
    val r = c
      .importDataset(Caller.Local, Nil, Some(PeriodImports.Dataset("local")), PeriodImports.DatasetBody(dump))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(r.roots, 2)
    val lba = c.period(HistoricalPeriod.Id("zzqauthlba")).fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(lba.parent.map(_.id), Some("zzqauthbronze"))
    val node = c.period(HistoricalPeriod.Id("zzqauth")).fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(node.children.map(_.id).toSet, Set("zzqauthbronze", "zzqauthiron"))
    assertEquals(c.search(Some(PeriodSearch.Text("Zzq Late")), None, None, None).map(_.id), List("zzqauthlba"))
    assertEquals(
      c.period(HistoricalPeriod.Id("zz_none")).left.map(_.problems.head).left.map(_.sentence),
      Left("No period 'zz_none'")
    )

  test("a fetch takes the configured collections; an unknown source, dataset or collection is refused"):
    val c = controllers()
    val r =
      c.fetch(Caller.Local, PeriodSources.PeriodoUrl("PeriodO"), Nil)
        .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(r.roots, 1)
    assertEquals(r.periods, 4)
    assert(c.period(HistoricalPeriod.Id("zzqother01")).isLeft)
    assert(
      c.fetch(Caller.Local, PeriodSources.PeriodoUrl("chronontology"), Nil)
        .left
        .exists(_.problems.sentences.mkString("; ").contains("unknown period source"))
    )
    assert(
      c.fetch(Caller.Local, PeriodSources.PeriodoUrl("periodo"), List(PeriodSources.Collections("zzqnope")))
        .left
        .exists(_.problems.sentences.mkString("; ").contains("zzqnope"))
    )
    assert(
      c.importDataset(Caller.Local, Nil, Some(PeriodImports.Dataset("other")), PeriodImports.DatasetBody(dump))
        .left
        .exists(_.problems.sentences.mkString("; ").contains("unknown dataset 'other' (one of: "))
    )
    val dead =
      controllers(download = _ => Left(Problem.Unclassified(Legacy.Text("HTTP 503 from https://periodo.test/dump"))))
    assert(
      dead
        .fetch(Caller.Local, PeriodSources.PeriodoUrl("periodo"), Nil)
        .left
        .exists(_.problems.sentences.mkString("; ").contains("503"))
    )

  test("the seed's absence is the answer of a seed; a held lock refuses every write"):
    assertEquals(
      controllers()
        .seed(Caller.Local, replace = PeriodImports.Replace(false))
        .left
        .map(_.problems.head)
        .left
        .map(_.sentence),
      Left("no period seed here")
    )
    val gate = WriteGate()
    val c = controllers(gate = gate)
    val held = gate.exclusively(WriteGate.OperationName("restore")) {
      Right(
        (
          c.seed(Caller.Local, replace = PeriodImports.Replace(false)),
          c.importDataset(Caller.Local, Nil, None, PeriodImports.DatasetBody(dump)),
          c.fetch(Caller.Local, PeriodSources.PeriodoUrl("periodo"), Nil)
        )
      )
    }
    val (a, b, d) = held.fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assert(List(a, b, d).forall(_.isLeft), List(a, b, d))
