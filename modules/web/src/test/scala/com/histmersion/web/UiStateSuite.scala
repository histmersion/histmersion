package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.raquo.airstream.ownership.ManualOwner

/** The typed UiState: codecs, the hash round trip, merging a filter link, local persistence — and
  * the reported layer bug (#383). Runs in Node: no `location`, no `localStorage`, so the hash is not
  * mirrored and the store is in memory. */
class UiStateSuite extends munit.FunSuite:

  override def beforeEach(context: BeforeEach): Unit =
    UiState.mirrorHash = false
    UiState.store = Store.Memory()
    UiState.loadHash("")
    UiState.layers.clear()
    UiState.baseMap.clear()

  test("codecs: garbage decodes to the default, lists are cleaned, layers use their wire names") {
    import FieldCodec.given
    assertEquals(summon[FieldCodec[Option[Long]]].decode(List("x")), Some(None))
    assertEquals(summon[FieldCodec[Option[Long]]].decode(List("-1200")), Some(Some(-1200L)))
    assertEquals(summon[FieldCodec[List[String]]].decode(List(" a", "", "a", "b")), Some(List("a", "b")))
    assertEquals(summon[FieldCodec[Set[MapLayer]]].encode(Set(MapLayer.Media, MapLayer.Boundaries)),
      List("boundaries", "media"))
    assertEquals(summon[FieldCodec[BaseMap]].decode(List("nope")), None)
  }

  test("the hash round-trips every hash field, and a field it omits returns to its default") {
    UiState.set(UiState.tags := List("sea_peoples", "trade, long-distance"), UiState.q := Some("collapse"),
      UiState.inView := true, UiState.open := List(WindowKey.Source("a2020_b"), WindowKey.Jobs))()
    val h = UiState.hashString
    UiState.loadHash("")
    assertEquals(UiState.tags.now(), Nil)
    UiState.loadHash(h)
    assertEquals(UiState.tags.now(), List("sea_peoples", "trade, long-distance"))
    assertEquals(UiState.q.now(), Some("collapse"))
    assertEquals(UiState.inView.now(), true)
    assertEquals(UiState.open.now(), List(WindowKey.Source("a2020_b"), WindowKey.Jobs))
    assert(h.contains("open=source%3Aa2020_b") && h.contains("open=jobs"), h)
    // layers are a local preference, never in a shared link
    assert(!h.contains("layers"), h)
  }

  test("a /#tag= link inside a window ADDS the tag and keeps every other filter") {
    UiState.set(UiState.tags := List("bronze"), UiState.topics := List("collapse"), UiState.q := Some("ugarit"))()
    UiState.addHash("#tag=sea_peoples")
    assertEquals(UiState.tags.now(), List("bronze", "sea_peoples"))
    assertEquals(UiState.topics.now(), List("collapse"))
    assertEquals(UiState.q.now(), Some("ugarit"))
    // …and a scalar replaces
    UiState.addHash("#q=hittites")
    assertEquals(UiState.q.now(), Some("hittites"))
  }

  test("removing a chip removes that value only; clear all leaves the span and the view alone") {
    UiState.set(UiState.tags := List("a", "b"), UiState.from := Some(-1500L), UiState.tab := Some("articles"))()
    UiState.removeChip("tags", "a")
    assertEquals(UiState.tags.now(), List("b"))
    UiState.clearFilters()
    assertEquals(UiState.tags.now(), Nil)
    assertEquals(UiState.from.now(), Some(-1500L))
    assertEquals(UiState.tab.now(), Some("articles"))
  }

  test("#383: restored layers — the toggle AND the layer are on, and ONE click turns both off") {
    // what the browser remembered from the last visit
    UiState.store.put("histmersion.ui.layers", """["boundaries"]""")
    UiState.restoreLocal()

    // the map: a LayerSync following UiState.layers, exactly as MapView subscribes it
    val onMap = scala.collection.mutable.Set.empty[MapLayer]
    val sync  = LayerSync(attach = onMap += _, detach = onMap -= _)
    // the toggle button: shows UiState.layerOn(l), clicks set(!shown)
    var shown = false
    val owner = new ManualOwner
    UiState.layers.signal.foreach(sync(_))(using owner)
    UiState.layerOn(MapLayer.Boundaries).foreach(shown = _)(using owner)

    assert(shown, "the button shows the restored layer as on")
    assertEquals(onMap.toSet, Set(MapLayer.Boundaries), "…and the layer is on the map")

    UiState.setLayer(MapLayer.Boundaries, !shown) // one click
    assert(!shown, "one click turns the button off")
    assertEquals(onMap.toSet, Set.empty[MapLayer], "…and the layer with it")
    assertEquals(UiState.store.get("histmersion.ui.layers"), Some("[]"), "…and remembers that")
    owner.killSubscriptions()
  }

  // #383 in the browser: a click runs inside an Airstream transaction, where Var.set is deferred —
  // the store was written BEFORE the new value landed, so the layer came back off after a reload
  test("a layer switched on from an event handler (inside a transaction) is remembered as on") {
    UiState.setLayer(MapLayer.Sites, false)
    com.raquo.airstream.core.Transaction(_ => UiState.setLayer(MapLayer.Sites, true))
    assertEquals(UiState.layers.now(), Set(MapLayer.Sites))
    assertEquals(UiState.store.get("histmersion.ui.layers"), Some("""["sites"]"""))
    UiState.setLayer(MapLayer.Sites, false)
  }

  test("LayerSync is idempotent: replaying a state attaches nothing twice") {
    var attaches = 0
    val sync = LayerSync(attach = _ => attaches += 1, detach = _ => ())
    sync(Set(MapLayer.Sites)); sync(Set(MapLayer.Sites))
    assertEquals(attaches, 1)
    assertEquals(sync.attached, Set(MapLayer.Sites))
  }

  test("the pre-UiState layer memory is migrated once and removed") {
    UiState.store.put("histmersion.layers", """{"overlay":true,"media":false,"sites":true}""")
    UiState.restoreLocal()
    assertEquals(UiState.layers.now(), Set(MapLayer.Boundaries, MapLayer.Sites))
    assertEquals(UiState.store.get("histmersion.layers"), None)
    assertEquals(UiState.store.get("histmersion.ui.layers"), Some("""["boundaries","sites"]"""))
  }

  test("a filter link is recognised by its hash, anything else is not") {
    assertEquals(WindowManager.filterHash("/#tag=sea_peoples"), Some("#tag=sea_peoples"))
    assertEquals(WindowManager.filterHash("#cite-3"), None)
    assertEquals(WindowManager.filterHash("/source/a2020_b"), None)
  }
