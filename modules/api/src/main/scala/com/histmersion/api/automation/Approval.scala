package com.histmersion.api.automation

import com.histmersion.api.WireEnumValues

/** An approval's vocabularies as the wire spells them (the api's own copies of the model's). */
object Approval:

  /** A change an agent run proposed that a person may have to accept before code applies it: a source's status, a typed
    * disagreement edge between two sources, a goal's plan, or an article a run compiled. `blocking` = the run WAITS for
    * the decision (`waiting_approval`) and applies it itself when it resumes; the other kinds are applied by the
    * decision and the run never waits.
    */
  enum Kind(val wire: String, val blocking: Boolean):
    case StatusChange extends Kind("status-change", false)
    case Edge extends Kind("edge", false)
    case GoalPlan extends Kind("goal-plan", true)
    case ArticlePublish extends Kind("article-publish", true)

    /** An article's inferred coverage filter (infer-filter): approving records it on the article. */
    case ArticleFilter extends Kind("article-filter", false)

    /** The articles planned for a research area (propose-articles): approving lets its seeding manager compile them.
      */
    case ArticlePlan extends Kind("article-plan", false)

    /** A structured action for a process manager (hold, prioritise, skip, a settings change) that a MODEL proposed from
      * a hint (phase 3): applied only when a person approves it. `subject` = the manager.
      */
    case HintEffect extends Kind("hint-effect", false)

    /** About a goal or an article (`subject`), not a source. */
    def aboutSubject: com.histmersion.api.automation.Approval.Kind.AboutSubjectResult = this != StatusChange && this != Edge

    /** Its proposal is an article's coverage filter. */
    def proposesFilter: com.histmersion.api.automation.Approval.Kind.ProposesFilterResult = this == ArticleFilter

    /** Its proposal is a plan of articles. */
    def proposesArticles: com.histmersion.api.automation.Approval.Kind.ProposesArticlesResult = this == ArticlePlan

  object Kind:
    type AboutSubjectResult = AboutSubjectResult.T
    object AboutSubjectResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ProposesFilterResult = ProposesFilterResult.T
    object ProposesFilterResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ProposesArticlesResult = ProposesArticlesResult.T
    object ProposesArticlesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromWireS = FromWireS.T
    object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.automation.Approval.Kind.ParseS): Option[Kind] = values.find(_.wire == s.trim.toLowerCase)

    given WireEnumValues[Kind]:
      def all: Array[Kind] = Kind.values
      def wire(e: Kind): com.histmersion.api.automation.Approval.Kind.WireResult = e.wire
      def fromWire(s: com.histmersion.api.automation.Approval.Kind.FromWireS): Option[Kind] = parse(s)

  /** How a kind of proposal is handled: `Manual` waits for a person, `Auto` is applied at once (and still recorded,
    * already approved).
    */
  enum Mode(val wire: String):
    case Manual extends Mode("manual")
    case Auto extends Mode("auto")

  object Mode:
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.automation.Approval.Mode.ParseS): Option[Mode] = values.find(_.wire == s.trim.toLowerCase)

  /** `Failed` = a person approved it but applying it failed (the source changed or went away); the reason is kept in
    * `result`.
    */
  enum State(val wire: String):
    case Open extends State("open")
    case Approved extends State("approved")
    case Rejected extends State("rejected")
    case Failed extends State("failed")

  object State:
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.automation.Approval.State.ParseS): Option[State] = values.find(_.wire == s.trim.toLowerCase)

  /** Who decided an approval. `Autopilot` = the checked auto approvals (their note starts [[AutopilotNote]]); `Auto` =
    * applied the moment it was proposed, by an `agents.approvals.*` mode of `auto` (its result starts [[AutoResult]]);
    * `Person` = everything else that is decided.
    */
  enum DecidedBy(val wire: String, val describe: String):
    case Person extends DecidedBy("person", "decided by a person")
    case Autopilot extends DecidedBy("autopilot", "decided by the autopilot's checks")
    case Auto extends DecidedBy("auto", "applied when proposed (an auto approval mode)")

  object DecidedBy:
    /** The note every autopilot decision starts with (`AutopilotPolicy.note`). */
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfState = OfState.T
    object OfState extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfNote = OfNote.T
    object OfNote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfResult = OfResult.T
    object OfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val AutopilotNote = "autopilot:"

    /** The result of a proposal applied at once (`Approvals.propose`, an `auto` mode). */
    val AutoResult = "applied automatically"

    def parse(s: com.histmersion.api.automation.Approval.DecidedBy.ParseS): Option[DecidedBy] = values.find(_.wire == s.trim.toLowerCase)

    /** Who decided an approval in `state` with this `note` and `result`; `None` while it is open. The SQL that filters
      * and counts by it says the same (`AgentRepo`).
      */
    def of(state: com.histmersion.api.automation.Approval.DecidedBy.OfState, note: Option[com.histmersion.api.automation.Approval.DecidedBy.OfNote], result: Option[com.histmersion.api.automation.Approval.DecidedBy.OfResult]): Option[DecidedBy] =
      if state == State.Open.wire then None
      else if note.exists(_.startsWith(AutopilotNote)) then Some(Autopilot)
      else if note.isEmpty && result.exists(_.startsWith(AutoResult)) then Some(Auto)
      else Some(Person)
