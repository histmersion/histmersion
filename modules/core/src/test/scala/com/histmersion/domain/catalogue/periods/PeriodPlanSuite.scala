package com.histmersion
package domain.catalogue.periods

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.util.data.exchange.BundleEntities

import java.util.UUID
import com.histmersion.api.catalogue.Year
import com.histmersion.api.exchange.Bundle
import com.histmersion.lint.{tolerated, LintRule}

class PeriodPlanSuite extends munit.FunSuite:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def period(
      id: Bundle.Period.Id,
      label: Bundle.Period.Label,
      from: Year,
      to: Year,
      parent: Option[Bundle.Period.ParentId] = None
  ) =
    Bundle.Period(
      UUID.nameUUIDFromBytes(id.value.getBytes),
      UUID.nameUUIDFromBytes("origin".getBytes),
      id,
      label,
      None,
      Some(Bundle.Range(Some(from), Some(to))),
      None,
      parent,
      Bundle.Period.UpdatedAt("2026-01-01T00:00:00.000000Z")
    )

  test(
    "a period matched to one here under another id is its children's parent under that id; no match changes nothing"
  ):
    val es = BundleEntities(periods =
      List(
        period(
          Bundle.Period.Id("b_child"),
          Bundle.Period.Label("Child"),
          Year(-1500),
          Year(-1400),
          Some(Bundle.Period.ParentId("b_age"))
        ),
        period(Bundle.Period.Id("b_age"), Bundle.Period.Label("Age"), Year(-2000), Year(-1000))
      )
    )
    assertEquals(
      PeriodPlan
        .repoint(es, Map(Bundle.Period.ParentId("b_age") -> Bundle.Period.ParentId("h_age")))
        .periods
        .map(_.parentId),
      List(Some("h_age"), None)
    )
    assertEquals(PeriodPlan.repoint(es, Map.empty), es)

  test("two records of one period id are the same period when the span and the label's fingerprint agree"):
    val a = period(Bundle.Period.Id("zz_age"), Bundle.Period.Label("The Zz Age"), Year(-1500), Year(-1000))
    assert(PeriodPlan.samePeriod(a, a.modify(_.label).setTo(Bundle.Period.Label("zz age, the"))))
    assert(!PeriodPlan.samePeriod(a, a.modify(_.label).setTo(Bundle.Period.Label("The Zz Era"))))
    assert(
      !PeriodPlan.samePeriod(a, a.modify(_.period).setTo(Some(Bundle.Range(Some(Year(-1600)), Some(Year(-1000))))))
    )
