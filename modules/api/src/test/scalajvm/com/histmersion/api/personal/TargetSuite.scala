package com.histmersion.api.personal

import com.histmersion.{Latitude, Longitude, Year}
import io.circe.parser.decode
import io.circe.syntax.*
import com.histmersion.api.ItemKind

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
    assertEquals(Target.check(messy, ItemKind.Bookmarkable, "bookmark"), Right(Target.item(ItemKind.Source, "zz_a")))
    assertEquals(Target.check(point.copy(id = Some(ItemKey("x"))), ItemKind.Notable, "note").map(_.id), Right(None))
    assert(Target.check(Target(ItemKind.Period, from = Some(Year(-1))), ItemKind.Bookmarkable, "bookmark").isLeft)
    assert(Target.check(Target(ItemKind.View), ItemKind.Bookmarkable, "bookmark").isLeft)
    assert(Target.check(Target(ItemKind.View, view = Some(ViewState("#a=1"))), ItemKind.Notable, "note").isLeft)
    assertEquals(
      Target
        .check(Target(ItemKind.View, view = Some(ViewState("#a=1"))), ItemKind.Bookmarkable, "bookmark")
        .map(_.view.map(v => v.value: String)),
      Right(Some("a=1"))
    )
    assert(
      Target.check(Target(ItemKind.View, view = Some(ViewState("a" * 8193))), ItemKind.Bookmarkable, "bookmark").isLeft
    )

  test("where a target is, in words"):
    assertEquals(Target.where(point), "37.73° N, 22.75° W · 1300 BCE–1100 BCE")
    assertEquals(
      Target.where(Target(ItemKind.Period, from = Some(Year(-1200)), to = Some(Year(-1150)))),
      "1200 BCE–1150 BCE"
    )
    assertEquals(Target.where(Target.item(ItemKind.Source, "x")), "")
    assertEquals(
      Target.where(
        Target(ItemKind.View, lat = Some(Latitude(1)), lon = Some(Longitude(2)), zoom = Some(MapZoom(6.25)))
      ),
      "1.00° N, 2.00° E · zoom 6.3"
    )

  test("identity: an item is one; a point, a view and a period are each their own"):
    assertEquals(Target.item(ItemKind.Article, "a").identity, Some("article:a"))
    assertEquals(point.identity, None)

  test("the wire: kinds by name, out-of-range coordinates and unknown kinds refused"):
    assertEquals(decode[Target](point.asJson.noSpaces), Right(point))
    assert(point.asJson.noSpaces.contains("\"kind\":\"point\""))
    assert(decode[Target]("""{"kind":"point","lat":91,"lon":0}""").isLeft)
    assert(decode[Target]("""{"kind":"nonsense"}""").isLeft)
    assertEquals(
      decode[Target]("""{"kind":"source","id":" zz "}""").map(_.id.map(v => v.value: String)),
      Right(Some("zz"))
    )

  test("kinds parse with a plural and in any case"):
    assertEquals(ItemKind.parse("Sources"), Some(ItemKind.Source))
    assertEquals(ItemKind.parse("view"), Some(ItemKind.View))
    assertEquals(ItemKind.parse("x"), None)

  test("ids are lower-case UUIDs"):
    assert(Bookmark.Id.parse("0190F3C2-1111-7000-8000-000000000001").isRight)
    assert(Note.Id.parse("not").isLeft)
    assertEquals(PersonalIds.limit(None), PersonalIds.DefaultLimit)
    assertEquals(PersonalIds.limit(Some(99999)), PersonalIds.MaxLimit)
