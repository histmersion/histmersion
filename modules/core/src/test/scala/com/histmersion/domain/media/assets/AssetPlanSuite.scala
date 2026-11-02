package com.histmersion.domain.media.assets

import com.histmersion.api.text.SafeName

class AssetPlanSuite extends munit.FunSuite:

  test("an asset name taken in the survivor takes the other collection's label"):
    assertEquals(AssetPlan.freeName("fig3.png", "work", Set("fig3.png")), "fig3-work.png")
    assertEquals(AssetPlan.freeName("fig3.png", "work", Set("fig3.png", "fig3-work.png")), "fig3-work-2.png")
    assertEquals(AssetPlan.freeName("README", "my laptop", Set("README")), "README-my-laptop")

  test("a safe name keeps the last segment's letters, digits, dots, underscores and hyphens"):
    assertEquals(SafeName.of("a/b\\c d.png"), "c-d.png")
    assertEquals(SafeName.of("--x--"), "x")
    assertEquals(SafeName.of("///"), "image.png")
