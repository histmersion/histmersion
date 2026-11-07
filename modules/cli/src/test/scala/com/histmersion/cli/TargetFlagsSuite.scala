package com.histmersion.cli

import com.histmersion.api.ItemKind
import com.histmersion.api.catalogue.{Latitude, Year}
import com.histmersion.api.personal.ItemKey

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
      Right((ItemKind.Source, Some(ItemKey("zz_a"))))
    )
    assertEquals(
      TargetFlags.target(raw("points", lat = Some(37.7), lon = Some(22.7))).map(_.from),
      Right(Some(Year(-1300)))
    )
    assertEquals(TargetFlags.target(raw("point", lat = Some(97))).map(_.lat), Right(Some(Latitude(97.0))))
    assertEquals(TargetFlags.target(raw("thing")).left.map(_.exitCode), Left(ExitCode.Usage))
