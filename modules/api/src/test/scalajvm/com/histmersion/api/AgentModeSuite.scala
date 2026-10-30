package com.histmersion.api

/** The three-way agents control over the two switches underneath (autopilot on/off, drain on/off). */
class AgentModeSuite extends munit.FunSuite:
  import AgentMode.Step

  private val switches = for on <- List(false, true); draining <- List(false, true) yield (on, draining)

  test("the mode the switches stand for: off is paused, draining or not"):
    assertEquals(AgentMode.of(autopilotOn = true, draining = false), AgentMode.Working)
    assertEquals(AgentMode.of(autopilotOn = true, draining = true), AgentMode.Finishing)
    assertEquals(AgentMode.of(autopilotOn = false, draining = false), AgentMode.Paused)
    assertEquals(AgentMode.of(autopilotOn = false, draining = true), AgentMode.Paused)

  test("from every state of the switches, the steps reach the chosen mode, with no step to spare"):
    for (on, draining) <- switches; to <- AgentMode.values do
      val steps = AgentMode.steps(on, draining, to)
      val (on2, drain2) = steps.foldLeft((on, draining)) {
        case ((_, d), Step.Autopilot(v)) => (v, d)
        case ((a, _), Step.Drain(v))     => (a, v)
      }
      assertEquals(AgentMode.of(on2, drain2), to, s"$on/$draining → $to: $steps")
      assertEquals(steps.distinct, steps)
      if AgentMode.of(on, draining) == to && !(to == AgentMode.Paused && draining) then
        assertEquals(steps, Nil, s"$on/$draining → $to")

  test("pausing lifts a drain before turning the autopilot off; working lifts it before turning it on"):
    assertEquals(
      AgentMode.steps(autopilotOn = true, draining = true, AgentMode.Paused),
      List(Step.Drain(false), Step.Autopilot(false))
    )
    assertEquals(
      AgentMode.steps(autopilotOn = false, draining = true, AgentMode.Working),
      List(Step.Drain(false), Step.Autopilot(true))
    )
    assertEquals(AgentMode.steps(autopilotOn = true, draining = false, AgentMode.Finishing), List(Step.Drain(true)))

  test("the words: the control's three choices and the card's heading"):
    assertEquals(
      AgentMode.values.toList.map(Wording.agentMode),
      List("Working", "Finish current work, then pause", "Pause now")
    )
    assertEquals(Wording.agentsHeading(AgentMode.Finishing), "Research agents are finishing current work, then pausing")
    assertEquals(AgentMode.parse("finish"), Some(AgentMode.Finishing))
    assertEquals(AgentMode.parse("Paused"), Some(AgentMode.Paused))
    assertEquals(AgentMode.parse("stop"), None)
