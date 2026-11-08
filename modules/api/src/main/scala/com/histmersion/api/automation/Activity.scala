package com.histmersion
package api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Citekey

object Activity:
  type Actor = Actor.T
  object Actor extends RefinedType[String, Pure]
  type Cursor = Cursor.T
  object Cursor extends RefinedType[String, Pure]
  type Kind = Kind.T
  object Kind extends RefinedType[String, Pure]
  type Limit = Limit.T
  object Limit extends RefinedType[Int, Pure]
  type Subject = Subject.T
  object Subject extends RefinedType[String, Pure]


  enum EntryKind(val wire: EntryKind.Wire):
    case Run extends EntryKind(EntryKind.Wire("run"))
    case Job extends EntryKind(EntryKind.Wire("job"))
    case Log extends EntryKind(EntryKind.Wire("log"))
    case Decision extends EntryKind(EntryKind.Wire("decision"))

    /** The entry's kind as the activity list names it. */
    def wording: String = this match
      case Run      => ("Agent work")
      case Job      => ("Background job")
      case Log      => ("Note")
      case Decision => ("You decided")

  object EntryKind:
    type Wire = Wire.T
    object Wire extends RefinedType[String, Pure]


    def parse(s: Wire): Option[EntryKind] = values.find(_.wire == Wire((s).value.trim.toLowerCase))

  enum Writer(val wire: Writer.Wire):
    case Agent extends Writer(Writer.Wire("agent"))
    case Job extends Writer(Writer.Wire("job"))

  object Writer:
    type Wire = Wire.T
    object Wire extends RefinedType[String, Pure]

  enum Show(val wire: Show.Wire, val label: Show.Label):
    case Everything extends Show(Show.Wire("all"), Show.Label("Everything"))
    case Running extends Show(Show.Wire("running"), Show.Label("Running now"))
    case Agents extends Show(Show.Wire("agents"), Show.Label("Agent work"))
    case Jobs extends Show(Show.Wire("jobs"), Show.Label("Background jobs"))
    case Yours extends Show(Show.Wire("yours"), Show.Label("Your actions"))
    case Problems extends Show(Show.Wire("problems"), Show.Label("Problems"))

  object Show:
    type Wire = Wire.T
    object Wire extends RefinedType[String, Pure]
    type Label = Label.T
    object Label extends RefinedType[String, Pure]


    def parse(s: Wire): Option[Show] =
      val t = (s).value.trim.toLowerCase
      values.find(v => v.wire == Wire(t) || v.toString.toLowerCase == t)

  final case class Entry(
      key: Entry.Key,
      kind: Entry.Kind,
      at: Entry.At,
      state: Entry.State = Entry.State(""),
      what: Entry.What = Entry.What(""),
      text: Entry.Text = Entry.Text(""),
      error: Option[Entry.Error] = None,
      endedAt: Option[Entry.EndedAt] = None,
      goal: Option[Goal.Slug] = None,
      job: Option[Entry.Job] = None,
      by: Option[Entry.By] = None,
      citekey: Option[Citekey] = None,
      article: Option[Entry.Article] = None,
      queueItem: Option[Entry.QueueItem] = None,
      queueList: Option[Entry.QueueList] = None,
      approval: Option[Entry.Approval] = None,
      target: Option[Entry.Target] = None,
      targetKind: Option[Entry.TargetKind] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def entryKind: Option[EntryKind] = EntryKind.parse(kind)

  object Entry:
    type Id = Id.T
    object Id extends RefinedType[String, Pure]
    type Key = Key.T
    object Key extends RefinedType[String, Pure]
    export EntryKind.{Wire as Kind}
    type At = At.T
    object At extends RefinedType[String, Pure]
    type State = State.T
    object State extends RefinedType[String, Pure]
    type What = What.T
    object What extends RefinedType[String, Pure]
    type Text = Text.T
    object Text extends RefinedType[String, Pure]
    type Error = Error.T
    object Error extends RefinedType[String, Pure]
    type EndedAt = EndedAt.T
    object EndedAt extends RefinedType[String, Pure]
    export com.histmersion.api.SpatialHierarchyResult.Job.{Id as Job}
    type By = By.T
    object By extends RefinedType[String, Pure]
    export com.histmersion.api.research.ReviewGoal.{Slug as Article}
    export Goal.Plans.GoalLink.{QueueNumber as QueueItem}
    type QueueList = QueueList.T
    object QueueList extends RefinedType[String, Pure]
    type Approval = Approval.T
    object Approval extends RefinedType[Long, Pure]
    type Target = Target.T
    object Target extends RefinedType[String, Pure]
    type TargetKind = TargetKind.T
    object TargetKind extends RefinedType[String, Pure]


  final case class Counts(running: Counts.Running = Counts.Running(0), problemsWeek: Counts.ProblemsWeek = Counts.ProblemsWeek(0)) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object Counts:
    type Wire = Wire.T
    object Wire extends RefinedType[String, Pure]
    type Running = Running.T
    object Running extends RefinedType[Int, Pure]
    type ProblemsWeek = ProblemsWeek.T
    object ProblemsWeek extends RefinedType[Int, Pure]


  final case class Page(entries: List[Entry], next: Option[Page.Next] = None, counts: Counts = Counts())
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Page:
    export com.histmersion.api.Paging.{Cursor as Next}


  val MaxLimit = 200
  val DefaultLimit = 30

  def keyOf(kind: EntryKind, id: Entry.Id): Entry.Key = Entry.Key(s"${kind.wire}:$id")
