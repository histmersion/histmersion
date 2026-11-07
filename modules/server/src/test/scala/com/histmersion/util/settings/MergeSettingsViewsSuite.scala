package com.histmersion.util.settings

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.AppConfig

class MergeSettingsViewsSuite extends munit.FunSuite:

  test(
    "reference.conf's merge settings reach the planner field by field, the weights included; the apply wait stays behind"
  ):
    val c = AppConfig.load().merge
    val s = MergeSettingsViews.settings(c)
    assertEquals(
      (s.autoMerge, s.newAtMost, s.minhashSize, s.findingNearAtLeast, s.findingWithinKm),
      (c.autoMerge, c.newAtMost, c.minhashSize, c.findingNearAtLeast, c.findingWithinKm)
    )
    assertEquals((s.placeWithinKm, s.placeOverlapAtLeast), (c.placeWithinKm, c.placeOverlapAtLeast))
    assertEquals(s.weights.productIterator.toList, c.weights.productIterator.toList)
    assertEquals(s.weights.productElementNames.toList, c.weights.productElementNames.toList)
