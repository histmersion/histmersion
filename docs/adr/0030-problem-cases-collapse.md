# 0030. Problem cases collapse into parameterised homes; old stored entries are translated on read

- Status: accepted (owner decision 8 of 2026-11-19; queue row 18, decisions 1–14 of the problem-case analysis)
- Date: 2026-11-24

## Context

A `Problem` case exists only where something branches on it, where its HTTP status differs from its group's, or where
its data has a real shape (a list, an optional value, a nested choice, another problem); `architecture/problem-cases.tsv`
records which of the three justifies each case. The other cases fold into a sibling with a parameter. Problems are
stored as entries (`{"group", "case", "data", "text"}`) in `jobs.error`, `agent_steps.error`,
`candidate_watches.last_error`, `plan_pools.refusal` and inside stored records, so a renamed or merged case must still
read back: `Answer.translations` maps a former `(group, case)` to the entry of the case that holds it now, and
`ReadBackSuite` reads every entry of the frozen `problem-entries-v1.json` through it. A merge never changes a
sentence; the sentence ledger proves it.

## Decision

- **A step's failures are one case.** `Run.AtStep(step, failure: Run.Failure)` holds what were `AtStep`,
  `Unsettled`, `ProviderDown`, `BudgetSpent`, `NoValidAnswer`, `OutputCapped` and `Declined`. The case keeps the
  name `AtStep`, because `Run.Step` is the step's name, and the failure is `Run.Failure`, because a type named after
  another with a suffix (`StepFailure` beside `Step`) is what G9prefix refuses. A former entry keeps its `step` field; its other fields become
  the failure.
- **A declined answer names its step now, and an old one reads as its sentence.** `Declined` never named its step:
  it is the error of one attempt's row, and that row names the step. It is built with the step now. Its sentence still
  does not name the step, so the sentence is unchanged. A stored `Declined` entry has no step to give and no code acts
  on it, so its translation reads it as `Stored.Unrecognised` with the sentence it was stored with, rather than
  inventing a step.

- **No model is one case.** `Run.NoModel(role, shortfall: Run.Shortfall, passed)` holds what were `NoModel`
  (`Unusable`), `Exhausted(floor, frees)` and `Unassigned(asked)`. The two matches that read an exhausted chain
  (`Stopped.exhaustedRole`, `PickFailure`) match `NoModel(_, Shortfall.Exhausted(..), _)`.
- **Why a model is passed over is one case, and the list stays a case.** `Run.Skipped(skip: Run.Skip)` holds
  `Cooling`, `BelowFloor`, `BelowAllowance`, `AllowanceUnread`, `PlanOnly` and `AllowanceReached`. They stay problems,
  not values of a record, because they are written as problems: a model's state in the chain listing
  (`ChainEntry.problems`), an attempt's row (`AllowanceReached`) and a pause (`Halted(PlanQuota, Skipped(Cooling))`).
  `Run.PassedOver` stays a case, not the record the analysis proposed: `GET /api/agents/models` lists it among a
  role's problems on its own. Its list of reasons justifies it. The CLI's exit code for a cooling model or a reached
  allowance is unchanged (`Skipped(Cooling | AllowanceReached)` is "unavailable").

## Consequences

The case count falls with each slice (`ProblemCasesSuite` pins it, and the register's unjustified count). A
translation row stays as long as an old entry may be stored; dropping one needs a count query over the stored columns
first. Writing always uses the new shape.

## Code

`modules/problem/src/main/scala/com/histmersion/util/problem/Problem.scala` (`Run.AtStep`, `Run.Failure`),
`modules/problem/src/main/scala/com/histmersion/util/problem/Text.scala`,
`modules/api/src/main/scala/com/histmersion/api/problem/Answer.scala` (`translations`),
`modules/api/src/test/scalajvm/com/histmersion/api/problem/ReadBackSuite.scala`,
`tools/refactor/ProblemCaseRewrite.scala` and its `problem-cases-<slice>.conf` tables.
