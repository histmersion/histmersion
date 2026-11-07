package com.histmersion.domain.catalogue.periods

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.Caller
import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.util.data.maintenance.WriteGate

import java.util.concurrent.atomic.AtomicReference

/** The periods' controllers over the in-memory service, no server and no database: a PeriodO dump sent and fetched (one
  * collection, under one node per authority), the bundled seed missing, an unknown source and dataset refused, one
  * period shown with its parent and parts, and every write refused while the maintenance lock is held.
  */
class PeriodsControllersSuite extends munit.FunSuite:
  private val dump = scala.io.Source.fromResource("periods/periodo_dump.json")(using scala.io.Codec.UTF8).mkString

  private def controllers(
      download: PeriodSources.PeriodoUrl => Either[PeriodImports.Problem, PeriodImports.Document] = _ => Right(PeriodImports.Document(dump)),
      seed: () => Either[PeriodImports.Problem, PeriodImports.Document] = () => Left(PeriodImports.Problem("no period seed here")),
      gate: WriteGate = WriteGate()
  ): PeriodsControllers =
    val periods = PeriodService.InMemory(AtomicReference(CatalogueState.empty))
    val imports =
      new PeriodImports(periods, () => PeriodSources(com.histmersion.domain.catalogue.periods.PeriodSources.PeriodoUrl("https://periodo.test/dump"), List(com.histmersion.domain.catalogue.periods.PeriodSources.Collections("zzqauth"))), seed, download)
    PeriodsControllers(periods, imports, gate)

  test("a sent dump is grouped under its authorities; a period shows its parent and its parts"):
    val c = controllers()
    val r = c.importDataset(Caller.Local, Nil, Some(com.histmersion.domain.catalogue.periods.PeriodImports.Dataset("local")), PeriodImports.DatasetBody(dump)).fold(e => fail(e.message.value), identity)
    assertEquals(r.roots, 2)
    val lba = c.period(com.histmersion.api.catalogue.HistoricalPeriod.Id("zzqauthlba")).fold(e => fail(e.message.value), identity)
    assertEquals(lba.parent.map(_.id), Some("zzqauthbronze"))
    val node = c.period(com.histmersion.api.catalogue.HistoricalPeriod.Id("zzqauth")).fold(e => fail(e.message.value), identity)
    assertEquals(node.children.map(_.id).toSet, Set("zzqauthbronze", "zzqauthiron"))
    assertEquals(c.search(Some(com.histmersion.domain.catalogue.periods.PeriodSearch.Text("Zzq Late")), None, None, None).map(_.id), List("zzqauthlba"))
    assertEquals(c.period(com.histmersion.api.catalogue.HistoricalPeriod.Id("zz_none")).left.map(_.message), Left("No period 'zz_none'"))

  test("a fetch takes the configured collections; an unknown source, dataset or collection is refused"):
    val c = controllers()
    val r = c.fetch(Caller.Local, com.histmersion.domain.catalogue.periods.PeriodSources.PeriodoUrl("PeriodO"), Nil).fold(e => fail(e.message.value), identity)
    assertEquals(r.roots, 1)
    assertEquals(r.periods, 4)
    assert(c.period(com.histmersion.api.catalogue.HistoricalPeriod.Id("zzqother01")).isLeft)
    assert(c.fetch(Caller.Local, com.histmersion.domain.catalogue.periods.PeriodSources.PeriodoUrl("chronontology"), Nil).left.exists(_.message.value.contains("unknown period source")))
    assert(c.fetch(Caller.Local, com.histmersion.domain.catalogue.periods.PeriodSources.PeriodoUrl("periodo"), List(com.histmersion.domain.catalogue.periods.PeriodSources.Collections("zzqnope"))).left.exists(_.message.value.contains("zzqnope")))
    assert(c.importDataset(Caller.Local, Nil, Some(com.histmersion.domain.catalogue.periods.PeriodImports.Dataset("other")), PeriodImports.DatasetBody(dump)).left.exists(_.message.value.contains("seed or local")))
    val dead = controllers(download = _ => Left(PeriodImports.Problem("HTTP 503 from https://periodo.test/dump")))
    assert(dead.fetch(Caller.Local, com.histmersion.domain.catalogue.periods.PeriodSources.PeriodoUrl("periodo"), Nil).left.exists(_.message.value.contains("503")))

  test("the seed's absence is the answer of a seed; a held lock refuses every write"):
    assertEquals(controllers().seed(Caller.Local, replace = com.histmersion.domain.catalogue.periods.PeriodImports.Replace(false)).left.map(_.message), Left("no period seed here"))
    val gate = WriteGate()
    val c = controllers(gate = gate)
    val held = gate.exclusively(com.histmersion.util.data.maintenance.WriteGate.OperationName("restore")) {
      Right(
        (
          c.seed(Caller.Local, replace = com.histmersion.domain.catalogue.periods.PeriodImports.Replace(false)),
          c.importDataset(Caller.Local, Nil, None, PeriodImports.DatasetBody(dump)),
          c.fetch(Caller.Local, com.histmersion.domain.catalogue.periods.PeriodSources.PeriodoUrl("periodo"), Nil)
        )
      )
    }
    val (a, b, d) = held.fold(e => fail(e.message.value), identity)
    assert(List(a, b, d).forall(_.isLeft), List(a, b, d))
