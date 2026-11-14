package com.histmersion
package api.personal
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.Problem

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.catalogue.{Latitude, Longitude, Year}
import com.histmersion.api.ItemKind
import com.histmersion.api.Paging

class TargetSuite extends munit.FunSuite:

  private val point = Target(
    ItemKind.Point,
    lat = Some(Latitude(37.731)),
    lon = Some(Longitude(-22.75)),
    from = Some(Year(-1300)),
    to = Some(Year(-1100))
  )

  test("check keeps the fields a kind uses and drops the rest"):
    val messy = Target(ItemKind.Source, Some(ItemKey("  zz_a ")), lat = Some(Latitude(1)), view = Some(ViewState("x")))
    assertEquals(
      Target.check(messy, ItemKind.Bookmarkable, Problem.Field("bookmark")),
      Right(Target.item(ItemKind.Source, ItemKey("zz_a")))
    )
    assertEquals(
      Target.check(point.modify(_.id).setTo(Some(ItemKey("x"))), ItemKind.Notable, Problem.Field("note")).map(_.id),
      Right(None)
    )
    assert(
      Target
        .check(Target(ItemKind.Period, from = Some(Year(-1))), ItemKind.Bookmarkable, Problem.Field("bookmark"))
        .isLeft
    )
    assert(Target.check(Target(ItemKind.View), ItemKind.Bookmarkable, Problem.Field("bookmark")).isLeft)
    assert(
      Target
        .check(Target(ItemKind.View, view = Some(ViewState("#a=1"))), ItemKind.Notable, Problem.Field("note"))
        .isLeft
    )
    assertEquals(
      Target
        .check(Target(ItemKind.View, view = Some(ViewState("#a=1"))), ItemKind.Bookmarkable, Problem.Field("bookmark"))
        .map(_.view.map(v => v.value: String)),
      Right(Some("a=1"))
    )
    assert(
      Target
        .check(
          Target(ItemKind.View, view = Some(ViewState("a" * 8193))),
          ItemKind.Bookmarkable,
          Problem.Field("bookmark")
        )
        .isLeft
    )

  test("where a target is, in words"):
    assertEquals(Target.where(point), "37.73° N, 22.75° W · 1300 BCE–1100 BCE")
    assertEquals(
      Target.where(Target(ItemKind.Period, from = Some(Year(-1200)), to = Some(Year(-1150)))),
      "1200 BCE–1150 BCE"
    )
    assertEquals(Target.where(Target.item(ItemKind.Source, ItemKey("x"))), "")
    assertEquals(
      Target.where(
        Target(ItemKind.View, lat = Some(Latitude(1)), lon = Some(Longitude(2)), zoom = Some(MapZoom(6.25)))
      ),
      "1.00° N, 2.00° E · zoom 6.3"
    )

  test("identity: an item is one; a point, a view and a period are each their own"):
    assertEquals(Target.item(ItemKind.Article, ItemKey("a")).identity, Some("article:a"))
    assertEquals(point.identity, None)

  test("the wire: kinds by name, out-of-range coordinates and unknown kinds refused"):
    assertEquals(RawJson.decode[Target](point.asJson.noSpaces), Right(point))
    assert(point.asJson.noSpaces.contains("\"kind\":\"point\""))
    assert(
      RawJson.decode[Target]("""{"kind":"point","lat":91,"lon":0}""").isRight
    ) // the API layer accepts it; the server refuses it
    assert(RawJson.decode[Target]("""{"kind":"nonsense"}""").isLeft)
    assertEquals(
      RawJson.decode[Target]("""{"kind":"source","id":" zz "}""").map(_.id.map(v => v.value: String)),
      Right(Some("zz"))
    )

  test("kinds parse with a plural and in any case"):
    assertEquals(ItemKind.parse(ItemKind.Wire("Sources")), Some(ItemKind.Source))
    assertEquals(ItemKind.parse(ItemKind.Wire("view")), Some(ItemKind.View))
    assertEquals(ItemKind.parse(ItemKind.Wire("x")), None)

  test("the page size asked for is bounded"):
    assertEquals(PersonalIds.limit(None), PersonalIds.DefaultLimit)
    assertEquals(PersonalIds.limit(Some(Paging.Limit(99999))), PersonalIds.MaxLimit)
