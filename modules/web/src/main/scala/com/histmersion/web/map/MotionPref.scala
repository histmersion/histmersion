package com.histmersion
package web.map

import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.js
import scala.util.Try
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** THE answer to "should the map animate now?" (#4718). Every motion of the map reads it: the clusters' regroup on a
  * zoom ([[ClusterMotion]]), the names' easing (`MapLabels`), the camera's zoom and pan easing (`MapLibreSurface`:
  * every call the app makes, and MapLibre's own gestures through its `reduceMotion` option), and the stylesheet's map
  * transitions (the root element's `data-map-motion`). The choice is `UiState.mapMotion` (per device), handed over by
  * [[choose]]; the system's setting is followed live (a `matchMedia` change listener). In Node (the unit tests) there
  * is no system setting: it reads as off.
  */
object MotionPref:
  enum SystemReduced derives CanEqual:
    case Reduced, Full
  object SystemReduced:
    extension (systemReduced: SystemReduced) def reduced: Boolean = systemReduced == Reduced
  enum Animates derives CanEqual:
    case Animates, Still
  object Animates:
    extension (animates: Animates) def animates: Boolean = animates == Animates
  enum HeldBySystem derives CanEqual:
    case Held, Free
  object HeldBySystem:
    extension (heldBySystem: HeldBySystem) def held: Boolean = heldBySystem == Held

  val Query = "(prefers-reduced-motion: reduce)"

  // `var`s: read by `animate()` from anywhere, at once — a Var's value set inside an Airstream transaction
  // would read the old one until it ends
  private var choiceNow: MapMotion = MapMotion.System
  private var systemNow: SystemReduced = SystemReduced.Full
  private var installed = false
  private val state: Var[(MapMotion, SystemReduced)] = Var((MapMotion.System, SystemReduced.Full))

  /** Read the system's setting and follow its changes (once; also done by the first read). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def install(): Unit =
    if !installed then
      installed = true
      if js.typeOf(js.Dynamic.global.window) != "undefined" &&
        js.typeOf(dom.window.asInstanceOf[js.Dynamic].matchMedia) == "function"
      then
        Try(dom.window.matchMedia(Query)).toOption.filter(_ != null).foreach { mq =>
          systemNow = (if mq.matches then SystemReduced.Reduced else SystemReduced.Full)
          val listener: js.Function1[js.Dynamic, Unit] = e => {
            systemNow = (if e.matches.asInstanceOf[Boolean] then SystemReduced.Reduced else SystemReduced.Full);
            changed()
          }
          val d = mq.asInstanceOf[js.Dynamic]
          // Safari before 14 knows only the older addListener
          if js.typeOf(d.addEventListener) == "function" then d.addEventListener("change", listener)
          else d.addListener(listener)
        }
      changed()

  /** The reader's choice (from `UiState.mapMotion`). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def choose(m: MapMotion): Unit =
    install()
    choiceNow = m
    changed()

  def choice: MapMotion = choiceNow

  /** Does the system ask for reduced motion? */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def systemReduced: Boolean = { install(); systemNow.reduced }

  /** Should the map animate now? */
  def animate(): Boolean = { install(); MapMotion.animates(choice, systemNow) }

  /** Whether the map animates, as it changes. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val animates: Signal[Animates] =
    state.signal.map((m, r) => (if MapMotion.animates(m, r) then Animates.Animates else Animates.Still))

  /** Whether the menu says the map is still because the system asks for it. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val heldBySystem: Signal[HeldBySystem] =
    state.signal.map((m, r) => (if MapMotion.heldBySystem(m, r) then HeldBySystem.Held else HeldBySystem.Free))

  /** For a test: the system's setting as if the browser had reported it. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private[web] def systemChanged(reduced: SystemReduced): Unit =
    installed = true
    systemNow = reduced
    changed()

  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  private def changed(): Unit =
    state.set((choiceNow, systemNow))
    if js.typeOf(js.Dynamic.global.document) != "undefined" then
      Try(dom.document.documentElement.setAttribute("data-map-motion", MapMotion.attribute(animate())))
