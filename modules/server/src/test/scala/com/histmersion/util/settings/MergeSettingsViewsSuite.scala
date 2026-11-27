package com.histmersion
package util.settings

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.AppConfig
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class MergeSettingsViewsSuite extends munit.FunSuite:

  test(
    "reference.conf's merge settings reach the planner field by field, the weights included; the apply wait stays behind"
  ):
    val c = AppConfig.load().merge
    val s = MergeSettingsViews.settings(c)
    assertEquals(
      (s.autoMerge.value, s.newAtMost.value, s.minhashSize.value, s.findingNearAtLeast.value, s.findingWithinKm.value),
      (c.autoMerge.value, c.newAtMost.value, c.minhashSize.value, c.findingNearAtLeast.value, c.findingWithinKm.value)
    )
    assertEquals(
      (s.placeWithinKm.value, s.placeOverlapAtLeast.value),
      (c.placeWithinKm.value, c.placeOverlapAtLeast.value)
    )
    assertEquals(s.weights.productIterator.toList, c.weights.productIterator.toList)
    assertEquals(s.weights.productElementNames.toList, c.weights.productElementNames.toList)
