package com.histmersion
package api

/** The three-way agents control over the two switches underneath (autopilot on/off, drain on/off). */

import com.histmersion.api.automation.AgentMode
class AgentModeSuite extends munit.FunSuite:
  import com.histmersion.api.automation.AgentMode.Step

  private val switches = for on <- List(false, true); draining <- List(false, true)
  yield (Step.Autopilot.On(on), Step.Drain.On(draining))

  test("the mode the switches stand for: off is paused, draining or not"):
    assertEquals(
      AgentMode.of(autopilotOn = Step.Autopilot.On(true), draining = Step.Drain.On(false)),
      AgentMode.Working
    )
    assertEquals(
      AgentMode.of(autopilotOn = Step.Autopilot.On(true), draining = Step.Drain.On(true)),
      AgentMode.Finishing
    )
    assertEquals(
      AgentMode.of(autopilotOn = Step.Autopilot.On(false), draining = Step.Drain.On(false)),
      AgentMode.Paused
    )
    assertEquals(AgentMode.of(autopilotOn = Step.Autopilot.On(false), draining = Step.Drain.On(true)), AgentMode.Paused)

  test("from every state of the switches, the steps reach the chosen mode, with no step to spare"):
    for (on, draining) <- switches; to <- AgentMode.values do
      val steps = AgentMode.steps(on, draining, to)
      val (on2, drain2) = steps.foldLeft((on, draining)) {
        case ((_, d), Step.Autopilot(v)) => (v, d)
        case ((a, _), Step.Drain(v))     => (a, v)
      }
      assertEquals(AgentMode.of(on2, drain2), to, s"$on/$draining → $to: $steps")
      assertEquals(steps.distinct, steps)
      if AgentMode.of(on, draining) == to && !(to == AgentMode.Paused && draining.value) then
        assertEquals(steps, Nil, s"$on/$draining → $to")

  test("pausing lifts a drain before turning the autopilot off; working lifts it before turning it on"):
    assertEquals(
      AgentMode.steps(autopilotOn = Step.Autopilot.On(true), draining = Step.Drain.On(true), AgentMode.Paused),
      List(Step.Drain(Step.Drain.On(false)), Step.Autopilot(Step.Autopilot.On(false)))
    )
    assertEquals(
      AgentMode.steps(autopilotOn = Step.Autopilot.On(false), draining = Step.Drain.On(true), AgentMode.Working),
      List(Step.Drain(Step.Drain.On(false)), Step.Autopilot(Step.Autopilot.On(true)))
    )
    assertEquals(
      AgentMode.steps(autopilotOn = Step.Autopilot.On(true), draining = Step.Drain.On(false), AgentMode.Finishing),
      List(Step.Drain(Step.Drain.On(true)))
    )

  test("the words: the control's three choices and the card's heading"):
    assertEquals(
      AgentMode.values.toList.map(_.wording),
      List("Working", "Finish current work, then pause", "Pause now")
    )
    assertEquals(AgentMode.Finishing.heading, "Research agents are finishing current work, then pausing")
    assertEquals(AgentMode.parse(AgentMode.Wire("finish")), Some(AgentMode.Finishing))
    assertEquals(AgentMode.parse(AgentMode.Wire("Paused")), Some(AgentMode.Paused))
    assertEquals(AgentMode.parse(AgentMode.Wire("stop")), None)
