package com.histmersion.cli

import com.histmersion.api.ItemKind

class TargetFlagsSuite extends munit.FunSuite:

  private def raw(
      kind: String,
      id: Option[String] = None,
      lat: Option[Double] = None,
      lon: Option[Double] = None,
      zoom: Option[Double] = None
  ) = TargetFlags.Raw(kind, id, lat, lon, zoom, Some(-1300), Some(-1100), None)

  test("an item, a point, out-of-range values and an unknown kind"):
    assertEquals(
      TargetFlags.target(raw("source", Some("zz_a"))).map(t => (t.kind, t.itemKey)),
      Right((ItemKind.Source, Some("zz_a")))
    )
    assertEquals(
      TargetFlags.target(raw("points", lat = Some(37.7), lon = Some(22.7))).map(_.from.map(v => v.value: Int)),
      Right(Some(-1300))
    )
    assert(TargetFlags.target(raw("point", lat = Some(97))).left.exists(_.message.contains("--lat")))
    assertEquals(TargetFlags.target(raw("thing")).left.map(_.exitCode), Left(ExitCode.Usage))
