package com.histmersion
package domain.automation

import com.histmersion.WireEnumValues
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

object Approval:
  type Id = Id.T
  object Id extends Newtype[Long, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]:
    private def text(n: Note): String = n.value

    extension (n: Note)
      def byAutopilot: Boolean = text(n).startsWith(DecidedBy.AutopilotNote)

      def withoutAutopilotMark: Note = Note(text(n).stripPrefix(DecidedBy.AutopilotNote).trim)

      def approvedByAutopilot: Boolean = text(n).startsWith(s"${DecidedBy.AutopilotNote} approved")
  type Outcome = Outcome.T
  object Outcome extends Newtype[String, Pure]:
    extension (o: Outcome) def appliedAutomatically: Boolean = o.value.startsWith(DecidedBy.AutoResult)

  enum Kind(val wire: Kind.Wire, val blocking: Kind.Blocking) derives CanEqual:
    case StatusChange extends Kind(Kind.Wire("status-change"), Kind.Blocking(false))
    case Edge extends Kind(Kind.Wire("edge"), Kind.Blocking(false))
    case GoalPlan extends Kind(Kind.Wire("goal-plan"), Kind.Blocking(true))
    case ArticlePublish extends Kind(Kind.Wire("article-publish"), Kind.Blocking(true))

    case ArticleFilter extends Kind(Kind.Wire("article-filter"), Kind.Blocking(false))

    case ArticlePlan extends Kind(Kind.Wire("article-plan"), Kind.Blocking(false))

    case HintEffect extends Kind(Kind.Wire("hint-effect"), Kind.Blocking(false))

    def aboutSubject: Boolean = (this != StatusChange && this != Edge)

    def proposesFilter: Boolean = (this == ArticleFilter)

    def proposesArticles: Boolean = (this == ArticlePlan)

  object Kind:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
    type Blocking = Blocking.T
    object Blocking extends Newtype[Boolean, Pure]

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def parse(s: Wire): Option[Kind] = values.find(_.wire == Wire((s).value.trim.toLowerCase))

    given WireEnumValues[Kind]:
      def all: Array[Kind] = values
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def wire(e: Kind): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def fromWire(s: WireEnumValues.Wire): Option[Kind] = parse(Wire((s).value))

  enum Mode(val wire: Mode.Wire) derives CanEqual:
    case Manual extends Mode(Mode.Wire("manual"))
    case Auto extends Mode(Mode.Wire("auto"))

  object Mode:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def parse(s: Wire): Option[Mode] = values.find(_.wire == Wire((s).value.trim.toLowerCase))

  enum State(val wire: State.Wire) derives CanEqual:
    case Open extends State(State.Wire("open"))
    case Approved extends State(State.Wire("approved"))
    case Rejected extends State(State.Wire("rejected"))
    case Failed extends State(State.Wire("failed"))

  object State:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def parse(s: Wire): Option[State] = values.find(_.wire == Wire((s).value.trim.toLowerCase))

  enum DecidedBy(val wire: DecidedBy.Wire, val describe: DecidedBy.Describe):
    case Person extends DecidedBy(DecidedBy.Wire("person"), DecidedBy.Describe("decided by a person"))
    case Autopilot
        extends DecidedBy(DecidedBy.Wire("autopilot"), DecidedBy.Describe("decided by the autopilot's checks"))
    case Auto
        extends DecidedBy(DecidedBy.Wire("auto"), DecidedBy.Describe("applied when proposed (an auto approval mode)"))

  object DecidedBy:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
    type Describe = Describe.T
    object Describe extends Newtype[String, Pure]

    val AutopilotNote = "autopilot:"

    val AutoResult = "applied automatically"

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def parse(s: Wire): Option[DecidedBy] = values.find(_.wire == Wire((s).value.trim.toLowerCase))

    def of(state: State.Wire, note: Option[Note], result: Option[Outcome]): Option[DecidedBy] =
      if state == State.Open.wire then None
      else if note.exists(_.byAutopilot) then Some(Autopilot)
      else if note.isEmpty && result.exists(_.appliedAutomatically) then Some(Auto)
      else Some(Person)
