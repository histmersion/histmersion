package com.histmersion
package web

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.raquo.laminar.api.L.*
import com.raquo.airstream.ownership.ManualOwner
import com.histmersion.api.Api
import com.histmersion.api.library.Filter
import com.histmersion.web.ui.Layout
import com.histmersion.lint.{tolerated, LintRule}

/** The typed UiState: codecs, the hash round trip, merging a filter link, local persistence — and the reported layer
  * bug (#383). Runs in Node: no `location`, no `localStorage`, so the hash is not mirrored and the store is in memory.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
class UiStateSuite extends munit.FunSuite:

  override def beforeEach(context: BeforeEach): Unit =
    UiState.mirrorHash = false
    UiState.store = Store.Memory()
    UiState.loadHash(UiState.UrlHash(""))
    UiState.layers.clear()
    UiState.sources.clear()
    UiState.baseMap.clear()

  test("codecs: garbage decodes to the default, lists are cleaned, layers use their wire names") {
    import FieldCodec.given
    assertEquals(summon[FieldCodec[Option[Long]]].decode(List(FieldCodec.Token("x"))), Some(None))
    assertEquals(summon[FieldCodec[Option[Long]]].decode(List(FieldCodec.Token("-1200"))), Some(Some(-1200L)))
    assertEquals(
      summon[FieldCodec[List[String]]]
        .decode(List(FieldCodec.Token(" a"), FieldCodec.Token(""), FieldCodec.Token("a"), FieldCodec.Token("b"))),
      Some(List("a", "b"))
    )
    assertEquals(
      summon[FieldCodec[Set[MapLayer]]].encode(Set(MapLayer.Media, MapLayer.Boundaries)),
      List("boundaries", "media")
    )
    assertEquals(summon[FieldCodec[BaseMap]].decode(List(FieldCodec.Token("nope"))), None)
  }

  test("the hash round-trips every hash field, and a field it omits returns to its default") {
    UiState.set(
      UiState.tags := List("sea_peoples", "trade, long-distance"),
      UiState.q := Some("collapse"),
      UiState.inView := true,
      UiState.open := List(WindowKey.Source(WindowKey.Source.Citekey("a2020_b")), WindowKey.Jobs)
    )()
    val h = UiState.hashString
    UiState.loadHash(UiState.UrlHash(""))
    assertEquals(UiState.tags.now(), Nil)
    UiState.loadHash(h)
    assertEquals(UiState.tags.now(), List("sea_peoples", "trade, long-distance"))
    assertEquals(UiState.q.now(), Some("collapse"))
    assertEquals(UiState.inView.now(), true)
    assertEquals(UiState.open.now(), List(WindowKey.Source(WindowKey.Source.Citekey("a2020_b")), WindowKey.Jobs))
    assert(h.value.contains("open=source%3Aa2020_b") && h.value.contains("open=jobs"), h)
    // layers are a local preference, never in a shared link
    assert(!h.value.contains("layers"), h)
  }

  test("search editing preserves spaces between keystrokes; requests normalize the draft") {
    UiState.setSearch(Api.Search.Text("Bronze"))
    UiState.setSearch(Api.Search.Text("Bronze "))
    assertEquals(UiState.q.now(), Some("Bronze "))
    assertEquals(UiState.toFilter().q, Some("Bronze"))
    UiState.setSearch(Api.Search.Text(UiState.q.now().get + "Age"))
    assertEquals(UiState.q.now(), Some("Bronze Age"))
    assertEquals(UiState.toFilter().q, Some("Bronze Age"))
    UiState.setSearch(Api.Search.Text("   "))
    assertEquals(UiState.q.now(), Some("   "))
    assertEquals(UiState.toFilter().q, None)
    UiState.setSearch(Api.Search.Text(""))
    assertEquals(UiState.q.now(), None)
  }

  test("a /#tag= link inside a window ADDS the tag and keeps every other filter") {
    UiState.set(UiState.tags := List("bronze"), UiState.topics := List("collapse"), UiState.q := Some("ugarit"))()
    UiState.addHash(UiState.UrlHash("#tag=sea_peoples"))
    assertEquals(UiState.tags.now(), List("bronze", "sea_peoples"))
    assertEquals(UiState.topics.now(), List("collapse"))
    assertEquals(UiState.q.now(), Some("ugarit"))
    // …and a scalar replaces
    UiState.addHash(UiState.UrlHash("#q=hittites"))
    assertEquals(UiState.q.now(), Some("hittites"))
  }

  test("removing a chip removes that value only; clear all leaves the span and the view alone") {
    UiState.set(UiState.tags := List("a", "b"), UiState.from := Some(-1500L), UiState.tab := Some("articles"))()
    UiState.removeChip(UiState.RemoveChipKey("tags"), UiState.FilterTerm("a"))
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
    val sync = LayerSync(attach = onMap += _, detach = onMap -= _)
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

  // found in the browser: after back/forward the URL named other windows than the ones shown
  test("windows reconcile to the URL state: close the extra ones, open the missing ones, nothing twice") {
    val src = WindowKey.Source(WindowKey.Source.Citekey("a2020_b")); val acq = WindowKey.Acquire;
    val jobs = WindowKey.Jobs
    assertEquals(WindowManager.reconcile(List(acq), List(src)), (List(acq), List(src)))
    assertEquals(WindowManager.reconcile(List(src, acq), List(src, acq)), (Nil, Nil))
    assertEquals(WindowManager.reconcile(Nil, List(jobs, jobs)), (Nil, List(jobs)))
  }

  test("LayerSync is idempotent: replaying a state attaches nothing twice") {
    var attaches = 0
    val sync = LayerSync(attach = _ => attaches += 1, detach = _ => ())
    sync(Set(MapLayer.Sites)); sync(Set(MapLayer.Sites))
    assertEquals(attaches, 1)
    assertEquals(sync.attached, Set(MapLayer.Sites))
  }

  test("sources are on when nothing is remembered, even beside a remembered set of layers") {
    // a browser from before the switch existed: layers remembered, sources never written
    UiState.store.put("histmersion.ui.layers", """["boundaries"]""")
    UiState.restoreLocal()
    assertEquals(UiState.sources.now(), true)
    assertEquals(UiState.layers.now(), Set(MapLayer.Boundaries))
  }

  test("sources switched off are remembered as off, and on again as on") {
    com.raquo.airstream.core.Transaction(_ => UiState.set(UiState.sources := false)())
    assertEquals(UiState.store.get("histmersion.ui.sources"), Some("[]"))
    UiState.sources.clear()
    UiState.restoreLocal()
    assertEquals(UiState.sources.now(), false, "restored as off")
    UiState.set(UiState.sources := true)()
    UiState.restoreLocal()
    assertEquals(UiState.sources.now(), true)
    // a local preference, never in a shared link
    assert(!UiState.hashString.value.contains("sources"), UiState.hashString)
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

  // ── #406: one title quick-filter for every tab; #402: the more-filters row starts open ──────

  test("title: in the hash, round-trips, reaches the filter, and \"clear all\" clears it (not a chip)") {
    UiState.set(UiState.title := Some("kadesh"), UiState.tags := List("bronze"))()
    val h = UiState.hashString
    assert(h.value.contains("title=kadesh"), h)
    UiState.loadHash(UiState.UrlHash(""))
    assertEquals(UiState.title.now(), None)
    UiState.loadHash(h)
    assertEquals(UiState.title.now(), Some("kadesh"))
    assertEquals(UiState.toFilter().title, Some("kadesh"))
    assertEquals(UiState.toFilter(except = Set(UiState.ToFilterExcept("title"))).title, None)
    assert(!UiState.activeChips().exists(_.key.value == "title"), UiState.activeChips().toString)
    UiState.clearFilters()
    assertEquals(UiState.title.now(), None)
  }

  test("an old link with the removed per-tab search (aq=) loads without it and without error") {
    UiState.loadHash(UiState.UrlHash("#aq=troy&tag=bronze"))
    assertEquals(UiState.tags.now(), List("bronze"))
    assertEquals(UiState.title.now(), None)
    assert(!UiState.hashString.value.contains("aq="), UiState.hashString)
  }

  test("the Articles and Events tabs ask for the GLOBAL filter, title included") {
    UiState.set(
      UiState.status := List("current"),
      UiState.title := Some("troy"),
      UiState.from := Some(-1300L),
      UiState.to := Some(-1100L)
    )()
    val f = UiState.toFilter()
    assertEquals(UiState.articleQuery().filter, f.modify(_.limit).setTo(Layout.tabFetch.asLimit))
    assertEquals(UiState.eventQuery().filter, f.modify(_.limit).setTo(Layout.tabFetch.asLimit))
    assertEquals(UiState.articleQuery().filter.status, List("current"))
    assertEquals(UiState.eventQuery().filter.title, Some("troy"))
    assertEquals(UiState.articleQuery().text, None)
  }

  test(
    "more filters: closed by default (the filter panel shows words, place, period and topic); open when left open or one of its filters is set"
  ) {
    assert(!FilterBar.moreOpenInitially(None, someMoreSet = false))
    assert(FilterBar.moreOpenInitially(Some(FilterBar.StoredText("1")), someMoreSet = false))
    assert(!FilterBar.moreOpenInitially(Some(FilterBar.StoredText("0")), someMoreSet = false))
    assert(FilterBar.moreOpenInitially(Some(FilterBar.StoredText("0")), someMoreSet = true))
    assert(FilterBar.moreOpenInitially(None, someMoreSet = true, narrow = true))
  }
