package com.histmersion.domain.automation

import com.histmersion.WireEnumValues

object Approval:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Note = Note.T
  object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Outcome = Outcome.T
  object Outcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A change an agent run proposed that a person may have to accept before code applies it: a source's status, a typed
    * disagreement edge between two sources, a goal's plan, or an article a run compiled. `blocking` = the run WAITS for
    * the decision (`waiting_approval`) and applies it itself when it resumes; the other kinds are applied by the
    * decision and the run never waits.
    */
  enum Kind(val wire: Kind.Wire, val blocking: Kind.Blocking):
    case StatusChange extends Kind(_root_.com.histmersion.domain.automation.Approval.Kind.Wire("status-change"), _root_.com.histmersion.domain.automation.Approval.Kind.Blocking(false))
    case Edge extends Kind(_root_.com.histmersion.domain.automation.Approval.Kind.Wire("edge"), _root_.com.histmersion.domain.automation.Approval.Kind.Blocking(false))
    case GoalPlan extends Kind(_root_.com.histmersion.domain.automation.Approval.Kind.Wire("goal-plan"), _root_.com.histmersion.domain.automation.Approval.Kind.Blocking(true))
    case ArticlePublish extends Kind(_root_.com.histmersion.domain.automation.Approval.Kind.Wire("article-publish"), _root_.com.histmersion.domain.automation.Approval.Kind.Blocking(true))

    /** An article's inferred coverage filter (infer-filter): approving records it on the article. */
    case ArticleFilter extends Kind(_root_.com.histmersion.domain.automation.Approval.Kind.Wire("article-filter"), _root_.com.histmersion.domain.automation.Approval.Kind.Blocking(false))

    /** The articles planned for a research area (propose-articles): approving lets its seeding manager compile them.
      */
    case ArticlePlan extends Kind(_root_.com.histmersion.domain.automation.Approval.Kind.Wire("article-plan"), _root_.com.histmersion.domain.automation.Approval.Kind.Blocking(false))

    /** A structured action for a process manager (hold, prioritise, skip, a settings change) that a MODEL proposed from
      * a hint (phase 3): applied only when a person approves it. `subject` = the manager.
      */
    case HintEffect extends Kind(_root_.com.histmersion.domain.automation.Approval.Kind.Wire("hint-effect"), _root_.com.histmersion.domain.automation.Approval.Kind.Blocking(false))

    /** About a goal or an article (`subject`), not a source. */
    def aboutSubject: Boolean = (this != StatusChange && this != Edge)

    /** Its proposal is an article's coverage filter. */
    def proposesFilter: Boolean = (this == ArticleFilter)

    /** Its proposal is a plan of articles. */
    def proposesArticles: Boolean = (this == ArticlePlan)

  object Kind:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Blocking = Blocking.T
    object Blocking extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]


    def parse(s: com.histmersion.domain.automation.Approval.Kind.Wire): Option[Kind] = values.find(_.wire == _root_.com.histmersion.domain.automation.Approval.Kind.Wire((s).value.trim.toLowerCase))

    given WireEnumValues[Kind]:
      def all: Array[Kind] = Kind.values
      def wire(e: Kind): _root_.com.histmersion.WireEnumValues.Wire = _root_.com.histmersion.WireEnumValues.Wire((e.wire).value)
      def fromWire(s: _root_.com.histmersion.WireEnumValues.Wire): _root_.scala.Option[Kind] = parse(_root_.com.histmersion.domain.automation.Approval.Kind.Wire((s).value))

  /** How a kind of proposal is handled: `Manual` waits for a person, `Auto` is applied at once (and still recorded,
    * already approved).
    */
  enum Mode(val wire: Mode.Wire):
    case Manual extends Mode(_root_.com.histmersion.domain.automation.Approval.Mode.Wire("manual"))
    case Auto extends Mode(_root_.com.histmersion.domain.automation.Approval.Mode.Wire("auto"))

  object Mode:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    def parse(s: com.histmersion.domain.automation.Approval.Mode.Wire): Option[Mode] = values.find(_.wire == _root_.com.histmersion.domain.automation.Approval.Mode.Wire((s).value.trim.toLowerCase))

  /** `Failed` = a person approved it but applying it failed (the source changed or went away); the reason is kept in
    * `result`.
    */
  enum State(val wire: State.Wire):
    case Open extends State(_root_.com.histmersion.domain.automation.Approval.State.Wire("open"))
    case Approved extends State(_root_.com.histmersion.domain.automation.Approval.State.Wire("approved"))
    case Rejected extends State(_root_.com.histmersion.domain.automation.Approval.State.Wire("rejected"))
    case Failed extends State(_root_.com.histmersion.domain.automation.Approval.State.Wire("failed"))

  object State:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    def parse(s: com.histmersion.domain.automation.Approval.State.Wire): Option[State] = values.find(_.wire == _root_.com.histmersion.domain.automation.Approval.State.Wire((s).value.trim.toLowerCase))

  /** Who decided an approval. `Autopilot` = the checked auto approvals (their note starts [[AutopilotNote]]); `Auto` =
    * applied the moment it was proposed, by an `agents.approvals.*` mode of `auto` (its result starts [[AutoResult]]);
    * `Person` = everything else that is decided.
    */
  enum DecidedBy(val wire: DecidedBy.Wire, val describe: DecidedBy.Describe):
    case Person extends DecidedBy(_root_.com.histmersion.domain.automation.Approval.DecidedBy.Wire("person"), _root_.com.histmersion.domain.automation.Approval.DecidedBy.Describe("decided by a person"))
    case Autopilot extends DecidedBy(_root_.com.histmersion.domain.automation.Approval.DecidedBy.Wire("autopilot"), _root_.com.histmersion.domain.automation.Approval.DecidedBy.Describe("decided by the autopilot's checks"))
    case Auto extends DecidedBy(_root_.com.histmersion.domain.automation.Approval.DecidedBy.Wire("auto"), _root_.com.histmersion.domain.automation.Approval.DecidedBy.Describe("applied when proposed (an auto approval mode)"))

  object DecidedBy:
    /** The note every autopilot decision starts with (`AutopilotPolicy.note`). */
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Describe = Describe.T
    object Describe extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    val AutopilotNote = "autopilot:"

    /** The result of a proposal applied at once (`Approvals.propose`, an `auto` mode). */
    val AutoResult = "applied automatically"

    def parse(s: com.histmersion.domain.automation.Approval.DecidedBy.Wire): Option[DecidedBy] = values.find(_.wire == _root_.com.histmersion.domain.automation.Approval.DecidedBy.Wire((s).value.trim.toLowerCase))

    /** Who decided an approval in `state` with this `note` and `result`; `None` while it is open. The SQL that filters
      * and counts by it says the same (`AgentRepo`).
      */
    def of(state: com.histmersion.domain.automation.Approval.State.Wire, note: Option[com.histmersion.domain.automation.Approval.Note], result: Option[com.histmersion.domain.automation.Approval.Outcome]): Option[DecidedBy] =
      if state == State.Open.wire then None
      else if note.exists((_).value.startsWith(AutopilotNote)) then Some(Autopilot)
      else if note.isEmpty && result.exists((_).value.startsWith(AutoResult)) then Some(Auto)
      else Some(Person)
