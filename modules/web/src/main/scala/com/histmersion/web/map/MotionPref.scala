package com.histmersion.web.map

import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.js
import scala.util.Try

/** THE answer to "should the map animate now?" (#4718). Every motion of the map reads it: the clusters' regroup on a
  * zoom ([[ClusterMotion]]), the names' easing (`MapLabels`), the camera's zoom and pan easing (`MapLibreSurface`:
  * every call the app makes, and MapLibre's own gestures through its `reduceMotion` option), and the stylesheet's map
  * transitions (the root element's `data-map-motion`). The choice is `UiState.mapMotion` (per device), handed over by
  * [[choose]]; the system's setting is followed live (a `matchMedia` change listener). In Node (the unit tests) there
  * is no system setting: it reads as off.
  */
object MotionPref:
  type SystemReduced = SystemReduced.T
  object SystemReduced extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SystemNow = SystemNow.T
  object SystemNow extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Animates = Animates.T
  object Animates extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeldBySystem = HeldBySystem.T
  object HeldBySystem extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Query = "(prefers-reduced-motion: reduce)"

  // `var`s: read by `animate()` from anywhere, at once — a Var's value set inside an Airstream transaction
  // would read the old one until it ends
  private var choiceNow: MapMotion = MapMotion.System
  private var systemNow: com.histmersion.web.map.MotionPref.SystemNow = com.histmersion.web.map.MotionPref.SystemNow(false)
  private var installed = false
  private val state: Var[(MapMotion, com.histmersion.web.map.MotionPref.SystemReduced)] = Var((MapMotion.System, com.histmersion.web.map.MotionPref.SystemReduced(false)))

  /** Read the system's setting and follow its changes (once; also done by the first read). */
  def install(): Unit =
    if !installed then
      installed = true
      if js.typeOf(js.Dynamic.global.window) != "undefined" &&
        js.typeOf(dom.window.asInstanceOf[js.Dynamic].matchMedia) == "function"
      then
        Try(dom.window.matchMedia(Query)).toOption.filter(_ != null).foreach { mq =>
          systemNow = mq.matches
          val listener: js.Function1[js.Dynamic, Unit] = e => { systemNow = e.matches.asInstanceOf[Boolean]; changed() }
          val d = mq.asInstanceOf[js.Dynamic]
          // Safari before 14 knows only the older addListener
          if js.typeOf(d.addEventListener) == "function" then d.addEventListener("change", listener)
          else d.addListener(listener)
        }
      changed()

  /** The reader's choice (from `UiState.mapMotion`). */
  def choose(m: MapMotion): Unit =
    install()
    choiceNow = m
    changed()

  def choice: MapMotion = choiceNow

  /** Does the system ask for reduced motion? */
  def systemReduced: Boolean = { install(); systemNow }

  /** Should the map animate now? */
  def animate(): Boolean = MapMotion.animates(choice, systemReduced)

  /** Whether the map animates, as it changes. */
  val animates: Signal[com.histmersion.web.map.MotionPref.Animates] = state.signal.map(MapMotion.animates.tupled)

  /** Whether the menu says the map is still because the system asks for it. */
  val heldBySystem: Signal[com.histmersion.web.map.MotionPref.HeldBySystem] = state.signal.map(MapMotion.heldBySystem.tupled)

  /** For a test: the system's setting as if the browser had reported it. */
  private[web] def systemChanged(reduced: com.histmersion.web.map.MotionPref.SystemReduced): Unit =
    installed = true
    systemNow = reduced
    changed()

  private def changed(): Unit =
    state.set((choiceNow, systemNow))
    if js.typeOf(js.Dynamic.global.document) != "undefined" then
      Try(dom.document.documentElement.setAttribute("data-map-motion", MapMotion.attribute(animate())))
