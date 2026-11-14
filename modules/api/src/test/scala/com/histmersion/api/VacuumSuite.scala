package com.histmersion
package api
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.jobs.{JobDetail, JobInfo}
import com.histmersion.api.library.IngestMarkdown
import com.histmersion.api.maintenance.{Vacuum, VacuumPlan}
import com.histmersion.api.maintenance.Storage
import com.histmersion.api.jobs.JobKinds

/** `histmersion maintenance vacuum` (#1445): which tables a request names, the statement for each, and how a job says
  * what it does.
  */
class VacuumSuite extends munit.FunSuite:

  private val defaults = List(Vacuum.Table("places"), Vacuum.Table("place_names"), Vacuum.Table("finding_places"))
  private val existing = Set("places", "place_names", "finding_places", "findings", "events").map(Vacuum.Table(_))

  test("no table named: the configured ones; named ones are trimmed, lower-cased, de-duplicated, comma lists split"):
    assertEquals(
      Vacuum.plan(Vacuum.Request(), defaults, existing),
      Right(
        VacuumPlan(
          VacuumPlan.Analyze(false),
          List(
            Vacuum.Request.Tables("places"),
            Vacuum.Request.Tables("place_names"),
            Vacuum.Request.Tables("finding_places")
          )
        )
      )
    )
    assertEquals(
      Vacuum.plan(
        Vacuum.Request(
          analyze = Vacuum.Request.Analyze(true),
          tables = List(Vacuum.Request.Tables(" Findings "), Vacuum.Request.Tables("events,findings"))
        ),
        defaults,
        existing
      ),
      Right(
        VacuumPlan(VacuumPlan.Analyze(true), List(Vacuum.Request.Tables("findings"), Vacuum.Request.Tables("events")))
      )
    )

  test("a name that is no identifier, or no table of the store, is refused — and so is an empty list"):
    def refused(tables: List[Vacuum.Request.Tables], d: List[Vacuum.Table] = defaults) =
      Vacuum.plan(Vacuum.Request(tables = tables), d, existing).left.map(e => (e.code, e.message))
    assertEquals(
      refused(List(Vacuum.Request.Tables("places; DROP TABLE x"))).left.map(_._1),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      refused(List(Vacuum.Request.Tables("pg_catalog.pg_class"))).left.map(_._1),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      refused(List(Vacuum.Request.Tables("nope"))),
      Left(
        (Some(Write.CodeInvalid), Problem.NotFound.Entity(Problem.TypeName.of(Vacuum.Table), Problem.Raw("nope")))
      )
    )
    assertEquals(refused(Nil, Nil).left.map(_._1), Left(Some(Write.CodeInvalid)))

  test("the statement quotes the table; ANALYZE only when asked"):
    assertEquals(
      Vacuum.statement(Vacuum.Table("place_names"), analyze = Vacuum.Request.Analyze(false)),
      "VACUUM \"place_names\""
    )
    assertEquals(
      Vacuum.statement(Vacuum.Table("place_names"), analyze = Vacuum.Request.Analyze(true)),
      "VACUUM (ANALYZE) \"place_names\""
    )

  test("a job says what it vacuums, and each table's line what it reclaimed"):
    assertEquals(
      Vacuum.describe(
        VacuumPlan(
          VacuumPlan.Analyze(true),
          List(Vacuum.Request.Tables("places"), Vacuum.Request.Tables("place_names"))
        )
      ),
      "VACUUM (ANALYZE) of places, place_names"
    )
    assertEquals(
      Vacuum.describe(VacuumPlan(VacuumPlan.Analyze(false), List(Vacuum.Request.Tables("places")))),
      "VACUUM of places"
    )
    assertEquals(
      Vacuum.tableLine(
        Vacuum.Table("place_names"),
        Vacuum.DeadRows(1237921),
        Vacuum.DeadRows(0),
        ByteCount(3738000000L),
        ByteCount(3738000000L),
        Storage.Report.DurationMillis(81234)
      ),
      "place_names: 1237921 → 0 dead rows, 3.7 GB → 3.7 GB (81.2 s)"
    )
    val job = JobInfo(
      SpatialHierarchyResult.Job.Id("j1"),
      JobKinds.Wire(Vacuum.JobKind),
      JobInfo.Status("running"),
      None,
      None,
      None,
      None,
      JobInfo.CreatedAt("t"),
      JobInfo.UpdatedAt("t"),
      detail =
        Some(VacuumPlan.Detail(Some(VacuumPlan(VacuumPlan.Analyze(false), List(Vacuum.Request.Tables("places"))))))
    )
    assertEquals(JobDetail.subject(job), Some("VACUUM of places"))
    assert(IngestMarkdown.job(job).contains("- **vacuums:** VACUUM of places"))

  test("a request body: unknown keys refused, absent ones defaulted"):
    assertEquals(RawJson.parse("""{}""").flatMap(_.as[Vacuum.Request]), Right(Vacuum.Request()))
    assertEquals(
      RawJson.parse("""{"analyze":true,"tables":["places"]}""").flatMap(_.as[Vacuum.Request]),
      Right(Vacuum.Request(Vacuum.Request.Analyze(true), List(Vacuum.Request.Tables("places"))))
    )
    assert(RawJson.parse("""{"full":true}""").flatMap(_.as[Vacuum.Request]).isLeft)
