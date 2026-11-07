package com.histmersion.web.ui

import com.histmersion.testsupport.ComparedByValue.given

class ApiValuesSuite extends munit.FunSuite:

  test("AggregateUnit.Sources.param is None — not Some(\"overlap\")"):
    assertEquals(AggregateUnit.Sources.param, None)

  test("AggregateUnit.Findings.param is Some(\"findings\")"):
    assertEquals(AggregateUnit.Findings.param, Some("findings"))

  test("AggregateUnit.Starts.param is Some(\"starts\")"):
    assertEquals(AggregateUnit.Starts.param, Some("starts"))

  test("AggregateBy.Time.param is \"time\""):
    assertEquals(AggregateBy.Time.param, "time")

  test("AggregateBy values all have non-empty params"):
    for v <- AggregateBy.values do assert(v.param.nonEmpty, s"$v has empty param")

  test("SidebarTab covers all expected tabs"):
    val keys = SidebarTab.values.map(_.key).toSet
    assertEquals(keys, Set("sources", "articles", "events", "places", "pictures"))

  test("SidebarTab keys are all non-empty"):
    for t <- SidebarTab.values do
      assert(t.key.nonEmpty, s"$t has empty key")
      assert(t.label.nonEmpty, s"$t has empty label")

  test("SidebarTab keys are unique"):
    val keys = SidebarTab.values.map(_.key)
    assertEquals(keys.toSet.size, keys.length)
