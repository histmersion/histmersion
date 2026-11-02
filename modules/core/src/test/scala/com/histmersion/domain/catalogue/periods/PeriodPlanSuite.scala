package com.histmersion.domain.catalogue.periods

import com.histmersion.util.data.exchange.BundleEntities

import java.util.UUID
import com.histmersion.api.exchange.Bundle

class PeriodPlanSuite extends munit.FunSuite:

  private def period(id: String, label: String, from: Long, to: Long, parent: Option[String] = None) =
    Bundle.Period(
      UUID.nameUUIDFromBytes(id.getBytes),
      UUID.nameUUIDFromBytes("origin".getBytes),
      id,
      label,
      None,
      Some(Bundle.Range(Some(from), Some(to))),
      None,
      parent,
      "2026-01-01T00:00:00.000000Z"
    )

  test(
    "a period matched to one here under another id is its children's parent under that id; no match changes nothing"
  ):
    val es = BundleEntities(periods =
      List(period("b_child", "Child", -1500, -1400, Some("b_age")), period("b_age", "Age", -2000, -1000))
    )
    assertEquals(PeriodPlan.repoint(es, Map("b_age" -> "h_age")).periods.map(_.parentId), List(Some("h_age"), None))
    assertEquals(PeriodPlan.repoint(es, Map.empty), es)

  test("two records of one period id are the same period when the span and the label's fingerprint agree"):
    val a = period("zz_age", "The Zz Age", -1500, -1000)
    assert(PeriodPlan.samePeriod(a, a.copy(label = "zz age, the")))
    assert(!PeriodPlan.samePeriod(a, a.copy(label = "The Zz Era")))
    assert(!PeriodPlan.samePeriod(a, a.copy(period = Some(Bundle.Range(Some(-1600), Some(-1000))))))
