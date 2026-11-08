package com.histmersion
package domain.media.assets

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.text.SafeName
import com.histmersion.api.exchange.Bundle
import com.histmersion.domain.MergePlanner

class AssetPlanSuite extends munit.FunSuite:

  test("an asset name taken in the survivor takes the other collection's label"):
    assertEquals(AssetPlan.freeName(Bundle.Asset.Name("fig3.png"), MergePlanner.Row.Label("work"), Set(Bundle.Asset.Name("fig3.png"))), "fig3-work.png")
    assertEquals(AssetPlan.freeName(Bundle.Asset.Name("fig3.png"), MergePlanner.Row.Label("work"), Set(Bundle.Asset.Name("fig3.png"), Bundle.Asset.Name("fig3-work.png"))), "fig3-work-2.png")
    assertEquals(AssetPlan.freeName(Bundle.Asset.Name("README"), MergePlanner.Row.Label("my laptop"), Set(Bundle.Asset.Name("README"))), "README-my-laptop")

  test("a safe name keeps the last segment's letters, digits, dots, underscores and hyphens"):
    assertEquals(SafeName.of("a/b\\c d.png"), "c-d.png")
    assertEquals(SafeName.of("--x--"), "x")
    assertEquals(SafeName.of("///"), "image.png")
