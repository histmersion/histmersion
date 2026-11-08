package com.histmersion
package web.map

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.web.{Store, UiState}

/** The reader's "Map motion" (#4718): what each choice decides with and without the system's reduced-motion setting,
  * what MapLibre is told, the stylesheet's attribute, when the menu's note shows — and the choice kept per device. Runs
  * in Node: no `matchMedia`, so the system's setting is set by hand.
  */
class MotionPrefSuite extends munit.FunSuite:

  override def afterEach(context: AfterEach): Unit =
    MotionPref.systemChanged(MotionPref.SystemReduced(false))
    MotionPref.choose(MapMotion.System)

  test("follow the system animates unless the system asks for reduced motion; always and never ignore it"):
    for reduced <- List(false, true) do
      assertEquals(MapMotion.animates(MapMotion.System, reduced), !reduced)
      assertEquals(MapMotion.animates(MapMotion.Always, reduced), true)
      assertEquals(MapMotion.animates(MapMotion.Never, reduced), false)

  test("the note shows only while following the system and the system asks for reduced motion"):
    assert(MapMotion.heldBySystem(MapMotion.System, systemReduced = true))
    assert(!MapMotion.heldBySystem(MapMotion.System, systemReduced = false))
    assert(!MapMotion.heldBySystem(MapMotion.Always, systemReduced = true))
    assert(!MapMotion.heldBySystem(MapMotion.Never, systemReduced = true))

  test("MapLibre's reduceMotion: unset to follow the system (it reads it itself), false to always animate, true never"):
    assertEquals(MapMotion.mapLibreReduce(MapMotion.System), None)
    assertEquals(MapMotion.mapLibreReduce(MapMotion.Always), Some(false))
    assertEquals(MapMotion.mapLibreReduce(MapMotion.Never), Some(true))

  test("the stylesheet's attribute and the wire names; anything else is no choice"):
    assertEquals(MapMotion.attribute(true), "animate")
    assertEquals(MapMotion.attribute(false), "still")
    assertEquals(MapMotion.values.toList.map(_.wire), List("system", "always", "never"))
    MapMotion.values.foreach(m => assertEquals(MapMotion.fromWire(MapMotion.Wire(s" ${m.wire.value.toUpperCase} ")), Some(m)))
    assertEquals(MapMotion.fromWire(MapMotion.Wire("sometimes")), None)

  test("MotionPref follows the choice and a change of the system's setting, live"):
    MotionPref.choose(MapMotion.System)
    MotionPref.systemChanged(MotionPref.SystemReduced(false))
    assert(MotionPref.animate())
    MotionPref.systemChanged(MotionPref.SystemReduced(true))
    assert(!MotionPref.animate(), "the system asks for reduced motion: still")
    MotionPref.choose(MapMotion.Always)
    assert(MotionPref.animate(), "always: animates although the system asks")
    MotionPref.choose(MapMotion.Never)
    MotionPref.systemChanged(MotionPref.SystemReduced(false))
    assert(!MotionPref.animate(), "never: still although the system does not ask")

  test("a camera move eases only when the caller animates it and the map animates"):
    import com.histmersion.web.map.maplibre.MapLibreSurface
    MotionPref.choose(MapMotion.Always)
    MotionPref.systemChanged(MotionPref.SystemReduced(true))
    assert(MapLibreSurface.eases(true))
    assert(!MapLibreSurface.eases(false))
    val o = MapLibreSurface.motion(true, scala.scalajs.js.Dynamic.literal()).asInstanceOf[scala.scalajs.js.Dynamic]
    assertEquals(o.animate.asInstanceOf[Boolean], true)
    assertEquals(
      o.essential.asInstanceOf[Boolean],
      true,
      "essential: MapLibre does not drop it for the system's setting"
    )
    MotionPref.choose(MapMotion.Never)
    val n = MapLibreSurface.motion(true, scala.scalajs.js.Dynamic.literal()).asInstanceOf[scala.scalajs.js.Dynamic]
    assertEquals(n.animate.asInstanceOf[Boolean], false)

  test("the choice is kept per device (UiState.mapMotion); a kept value that is not a choice is the default"):
    UiState.mirrorHash = false
    UiState.store = Store.Memory()
    UiState.set(UiState.mapMotion := MapMotion.Always)()
    assertEquals(UiState.store.get("histmersion.ui.mapMotion"), Some("""["always"]"""))
    UiState.mapMotion.clear()
    UiState.restoreLocal()
    assertEquals(UiState.mapMotion.now(), MapMotion.Always)
    UiState.store.put("histmersion.ui.mapMotion", """["sometimes"]""")
    UiState.restoreLocal()
    assertEquals(UiState.mapMotion.now(), MapMotion.System)
    assert(!UiState.hashString.value.contains("mapMotion"), "never in a link")
    UiState.mapMotion.clear()
