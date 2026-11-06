package com.histmersion.api

import io.circe.parser.parse
import com.histmersion.api.jobs.{JobDetail, JobInfo}
import com.histmersion.api.library.IngestMarkdown
import com.histmersion.api.maintenance.{Vacuum, VacuumPlan}

/** `histmersion maintenance vacuum` (#1445): which tables a request names, the statement for each, and how a job says
  * what it does.
  */
class VacuumSuite extends munit.FunSuite:

  private val defaults = List("places", "place_names", "finding_places")
  private val existing = Set("places", "place_names", "finding_places", "findings", "events")

  test("no table named: the configured ones; named ones are trimmed, lower-cased, de-duplicated, comma lists split"):
    assertEquals(Vacuum.plan(Vacuum.Request(), defaults, existing), Right(VacuumPlan(false, defaults)))
    assertEquals(
      Vacuum.plan(Vacuum.Request(analyze = true, tables = List(" Findings ", "events,findings")), defaults, existing),
      Right(VacuumPlan(true, List("findings", "events")))
    )

  test("a name that is no identifier, or no table of the store, is refused — and so is an empty list"):
    def refused(tables: List[String], d: List[String] = defaults) =
      Vacuum.plan(Vacuum.Request(tables = tables), d, existing).left.map(e => (e.code, e.message))
    assertEquals(refused(List("places; DROP TABLE x")).left.map(_._1), Left(Some(Write.CodeInvalid)))
    assertEquals(refused(List("pg_catalog.pg_class")).left.map(_._1), Left(Some(Write.CodeInvalid)))
    assertEquals(refused(List("nope")), Left((Some(Write.CodeInvalid), "no such table: nope")))
    assertEquals(refused(Nil, Nil).left.map(_._1), Left(Some(Write.CodeInvalid)))

  test("the statement quotes the table; ANALYZE only when asked"):
    assertEquals(Vacuum.statement("place_names", analyze = false), "VACUUM \"place_names\"")
    assertEquals(Vacuum.statement("place_names", analyze = true), "VACUUM (ANALYZE) \"place_names\"")

  test("a job says what it vacuums, and each table's line what it reclaimed"):
    assertEquals(
      Vacuum.describe(VacuumPlan(true, List("places", "place_names"))),
      "VACUUM (ANALYZE) of places, place_names"
    )
    assertEquals(Vacuum.describe(VacuumPlan(false, List("places"))), "VACUUM of places")
    assertEquals(
      Vacuum.tableLine("place_names", 1237921, 0, 3738000000L, 3738000000L, 81234),
      "place_names: 1237921 → 0 dead rows, 3.7 GB → 3.7 GB (81.2 s)"
    )
    val job = JobInfo(
      com.histmersion.api.SpatialHierarchyResult.Job.Id("j1"),
      com.histmersion.api.jobs.JobKinds.Wire(Vacuum.JobKind),
      "running",
      None,
      None,
      None,
      None,
      "t",
      "t",
      detail = Some(VacuumPlan.Detail(Some(VacuumPlan(false, List("places")))))
    )
    assertEquals(JobDetail.subject(job), Some("VACUUM of places"))
    assert(IngestMarkdown.job(job).contains("- **vacuums:** VACUUM of places"))

  test("a request body: unknown keys refused, absent ones defaulted"):
    assertEquals(parse("""{}""").flatMap(_.as[Vacuum.Request]), Right(Vacuum.Request()))
    assertEquals(
      parse("""{"analyze":true,"tables":["places"]}""").flatMap(_.as[Vacuum.Request]),
      Right(Vacuum.Request(true, List("places")))
    )
    assert(parse("""{"full":true}""").flatMap(_.as[Vacuum.Request]).isLeft)
