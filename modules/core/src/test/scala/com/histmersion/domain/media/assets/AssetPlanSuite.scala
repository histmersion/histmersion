package com.histmersion.domain.media.assets

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.text.SafeName

class AssetPlanSuite extends munit.FunSuite:

  test("an asset name taken in the survivor takes the other collection's label"):
    assertEquals(AssetPlan.freeName(com.histmersion.api.exchange.Bundle.Asset.Name("fig3.png"), com.histmersion.domain.MergePlanner.Row.Label("work"), Set(com.histmersion.api.exchange.Bundle.Asset.Name("fig3.png"))), "fig3-work.png")
    assertEquals(AssetPlan.freeName(com.histmersion.api.exchange.Bundle.Asset.Name("fig3.png"), com.histmersion.domain.MergePlanner.Row.Label("work"), Set(com.histmersion.api.exchange.Bundle.Asset.Name("fig3.png"), com.histmersion.api.exchange.Bundle.Asset.Name("fig3-work.png"))), "fig3-work-2.png")
    assertEquals(AssetPlan.freeName(com.histmersion.api.exchange.Bundle.Asset.Name("README"), com.histmersion.domain.MergePlanner.Row.Label("my laptop"), Set(com.histmersion.api.exchange.Bundle.Asset.Name("README"))), "README-my-laptop")

  test("a safe name keeps the last segment's letters, digits, dots, underscores and hyphens"):
    assertEquals(SafeName.of("a/b\\c d.png"), "c-d.png")
    assertEquals(SafeName.of("--x--"), "x")
    assertEquals(SafeName.of("///"), "image.png")
