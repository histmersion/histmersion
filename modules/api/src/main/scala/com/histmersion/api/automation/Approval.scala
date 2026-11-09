package com.histmersion
package api.automation

import com.histmersion.api.WireEnumValues
import io.github.iltotore.iron.constraint.any.Pure

/** An approval's vocabularies as the wire spells them (the api's own copies of the model's). */
object Approval:
  type Note = Note.T
  object Note extends Newtype[String, Pure]:
    private def text(n: Note): String = n.value

    extension (n: Note)
      /** Is this the note of a decision the autopilot's checks took? */
      def byAutopilot: Boolean = text(n).startsWith(DecidedBy.AutopilotNote)

      /** The note without the mark that says the autopilot wrote it. */
      def withoutAutopilotMark: Note = Note(text(n).stripPrefix(DecidedBy.AutopilotNote).trim)
  type Outcome = Outcome.T
  object Outcome extends Newtype[String, Pure]:
    extension (o: Outcome)
      /** Is this the result of a proposal applied the moment it was proposed? */
      def appliedAutomatically: Boolean = o.value.startsWith(DecidedBy.AutoResult)

  /** A change an agent run proposed that a person may have to accept before code applies it: a source's status, a typed
    * disagreement edge between two sources, a goal's plan, or an article a run compiled. `blocking` = the run WAITS for
    * the decision (`waiting_approval`) and applies it itself when it resumes; the other kinds are applied by the
    * decision and the run never waits.
    */
  enum Kind(val wire: Kind.Wire, val blocking: Kind.Blocking) derives CanEqual:
    case StatusChange extends Kind(Kind.Wire("status-change"), Kind.Blocking(false))
    case Edge extends Kind(Kind.Wire("edge"), Kind.Blocking(false))
    case GoalPlan extends Kind(Kind.Wire("goal-plan"), Kind.Blocking(true))
    case ArticlePublish extends Kind(Kind.Wire("article-publish"), Kind.Blocking(true))

    /** An article's inferred coverage filter (infer-filter): approving records it on the article. */
    case ArticleFilter extends Kind(Kind.Wire("article-filter"), Kind.Blocking(false))

    /** The articles planned for a research area (propose-articles): approving lets its seeding manager compile them.
      */
    case ArticlePlan extends Kind(Kind.Wire("article-plan"), Kind.Blocking(false))

    /** A structured action for a process manager (hold, prioritise, skip, a settings change) that a MODEL proposed from
      * a hint (phase 3): applied only when a person approves it. `subject` = the manager.
      */
    case HintEffect extends Kind(Kind.Wire("hint-effect"), Kind.Blocking(false))

    /** About a goal or an article (`subject`), not a source. */
    def aboutSubject: Boolean = (this != StatusChange && this != Edge)

    /** Its proposal is an article's coverage filter. */
    def proposesFilter: Boolean = (this == ArticleFilter)

    /** Its proposal is a plan of articles. */
    def proposesArticles: Boolean = (this == ArticlePlan)

  object Kind:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
    type Blocking = Blocking.T
    object Blocking extends Newtype[Boolean, Pure]

    def parse(s: Wire): Option[Kind] = values.find(_.wire == Wire((s).value.trim.toLowerCase))

    given WireEnumValues[Kind]:
      def all: Array[Kind] = values
      def wire(e: Kind): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
      def fromWire(s: WireEnumValues.Wire): Option[Kind] = parse(Wire((s).value))

  /** How a kind of proposal is handled: `Manual` waits for a person, `Auto` is applied at once (and still recorded,
    * already approved).
    */
  enum Mode(val wire: Mode.Wire) derives CanEqual:
    case Manual extends Mode(Mode.Wire("manual"))
    case Auto extends Mode(Mode.Wire("auto"))

  object Mode:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def parse(s: Wire): Option[Mode] = values.find(_.wire == Wire((s).value.trim.toLowerCase))

  /** `Failed` = a person approved it but applying it failed (the source changed or went away); the reason is kept in
    * `result`.
    */
  enum State(val wire: State.Wire) derives CanEqual:
    case Open extends State(State.Wire("open"))
    case Approved extends State(State.Wire("approved"))
    case Rejected extends State(State.Wire("rejected"))
    case Failed extends State(State.Wire("failed"))

  object State:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def parse(s: Wire): Option[State] = values.find(_.wire == Wire((s).value.trim.toLowerCase))

  /** Who decided an approval. `Autopilot` = the checked auto approvals (their note starts [[AutopilotNote]]); `Auto` =
    * applied the moment it was proposed, by an `agents.approvals.*` mode of `auto` (its result starts [[AutoResult]]);
    * `Person` = everything else that is decided.
    */
  enum DecidedBy(val wire: DecidedBy.Wire, val describe: DecidedBy.Describe) derives CanEqual:
    case Person extends DecidedBy(DecidedBy.Wire("person"), DecidedBy.Describe("decided by a person"))
    case Autopilot
        extends DecidedBy(DecidedBy.Wire("autopilot"), DecidedBy.Describe("decided by the autopilot's checks"))
    case Auto
        extends DecidedBy(DecidedBy.Wire("auto"), DecidedBy.Describe("applied when proposed (an auto approval mode)"))

  object DecidedBy:
    /** The note every autopilot decision starts with (`AutopilotPolicy.note`). */
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
    type Describe = Describe.T
    object Describe extends Newtype[String, Pure]

    val AutopilotNote = "autopilot:"

    /** The result of a proposal applied at once (`Approvals.propose`, an `auto` mode). */
    val AutoResult = "applied automatically"

    def parse(s: Wire): Option[DecidedBy] = values.find(_.wire == Wire((s).value.trim.toLowerCase))

    /** Who decided an approval in `state` with this `note` and `result`; `None` while it is open. The SQL that filters
      * and counts by it says the same (`AgentRepo`).
      */
    def of(state: State.Wire, note: Option[Note], result: Option[Outcome]): Option[DecidedBy] =
      if state == State.Open.wire then None
      else if note.exists(_.byAutopilot) then Some(Autopilot)
      else if note.isEmpty && result.exists(_.appliedAutomatically) then Some(Auto)
      else Some(Person)
