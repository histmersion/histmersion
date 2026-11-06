package com.histmersion.api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

object Activity:

  type KeyOfResult = KeyOfResult.T
  object KeyOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeyOfId = KeyOfId.T
  object KeyOfId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum EntryKind(val wire: EntryKind.Wire):
    case Run extends EntryKind(_root_.com.histmersion.api.automation.Activity.EntryKind.Wire("run"))
    case Job extends EntryKind(_root_.com.histmersion.api.automation.Activity.EntryKind.Wire("job"))
    case Log extends EntryKind(_root_.com.histmersion.api.automation.Activity.EntryKind.Wire("log"))
    case Decision extends EntryKind(_root_.com.histmersion.api.automation.Activity.EntryKind.Wire("decision"))

    /** The entry's kind as the activity list names it. */
    def wording: com.histmersion.api.automation.Activity.EntryKind.WordingResult = this match
      case Run      => _root_.com.histmersion.api.automation.Activity.EntryKind.WordingResult("Agent work")
      case Job      => _root_.com.histmersion.api.automation.Activity.EntryKind.WordingResult("Background job")
      case Log      => _root_.com.histmersion.api.automation.Activity.EntryKind.WordingResult("Note")
      case Decision => _root_.com.histmersion.api.automation.Activity.EntryKind.WordingResult("You decided")

  object EntryKind:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type WordingResult = WordingResult.T
    object WordingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.automation.Activity.EntryKind.ParseS): Option[EntryKind] = values.find(_.wire == (s).value.trim.toLowerCase)

  enum Writer(val wire: Writer.Wire):
    case Agent extends Writer(_root_.com.histmersion.api.automation.Activity.Writer.Wire("agent"))
    case Job extends Writer(_root_.com.histmersion.api.automation.Activity.Writer.Wire("job"))

  object Writer:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum Show(val wire: Show.Wire, val label: Show.Label):
    case Everything extends Show(_root_.com.histmersion.api.automation.Activity.Show.Wire("all"), _root_.com.histmersion.api.automation.Activity.Show.Label("Everything"))
    case Running extends Show(_root_.com.histmersion.api.automation.Activity.Show.Wire("running"), _root_.com.histmersion.api.automation.Activity.Show.Label("Running now"))
    case Agents extends Show(_root_.com.histmersion.api.automation.Activity.Show.Wire("agents"), _root_.com.histmersion.api.automation.Activity.Show.Label("Agent work"))
    case Jobs extends Show(_root_.com.histmersion.api.automation.Activity.Show.Wire("jobs"), _root_.com.histmersion.api.automation.Activity.Show.Label("Background jobs"))
    case Yours extends Show(_root_.com.histmersion.api.automation.Activity.Show.Wire("yours"), _root_.com.histmersion.api.automation.Activity.Show.Label("Your actions"))
    case Problems extends Show(_root_.com.histmersion.api.automation.Activity.Show.Wire("problems"), _root_.com.histmersion.api.automation.Activity.Show.Label("Problems"))

  object Show:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.automation.Activity.Show.ParseS): Option[Show] =
      val t = (s).value.trim.toLowerCase
      values.find(v => v.wire == t || v.toString.toLowerCase == t)

  final case class Entry(
      key: Entry.Key,
      kind: Entry.Kind,
      at: Entry.At,
      state: Entry.State = _root_.com.histmersion.api.automation.Activity.Entry.State(""),
      what: Entry.What = _root_.com.histmersion.api.automation.Activity.Entry.What(""),
      text: Entry.Text = _root_.com.histmersion.api.automation.Activity.Entry.Text(""),
      error: Option[Entry.Error] = None,
      endedAt: Option[Entry.EndedAt] = None,
      goal: Option[com.histmersion.api.automation.Goal.Slug] = None,
      job: Option[Entry.Job] = None,
      by: Option[Entry.By] = None,
      citekey: Option[com.histmersion.api.library.Citekey] = None,
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
    type Key = Key.T
    object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type At = At.T
    object At extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type State = State.T
    object State extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type What = What.T
    object What extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Error = Error.T
    object Error extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EndedAt = EndedAt.T
    object EndedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Job = Job.T
    object Job extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type By = By.T
    object By extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Article = Article.T
    object Article extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type QueueItem = QueueItem.T
    object QueueItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type QueueList = QueueList.T
    object QueueList extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Approval = Approval.T
    object Approval extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Target = Target.T
    object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetKind = TargetKind.T
    object TargetKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Counts(running: Counts.Running = _root_.com.histmersion.api.automation.Activity.Counts.Running(0), problemsWeek: Counts.ProblemsWeek = _root_.com.histmersion.api.automation.Activity.Counts.ProblemsWeek(0)) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object Counts:
    type Running = Running.T
    object Running extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ProblemsWeek = ProblemsWeek.T
    object ProblemsWeek extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Page(entries: List[Entry], next: Option[Page.Next] = None, counts: Counts = Counts())
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Page:
    type Next = Next.T
    object Next extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  val MaxLimit = 200
  val DefaultLimit = 30

  def keyOf(kind: EntryKind, id: com.histmersion.api.automation.Activity.KeyOfId): com.histmersion.api.automation.Activity.KeyOfResult = _root_.com.histmersion.api.automation.Activity.KeyOfResult(s"${kind.wire}:$id")
