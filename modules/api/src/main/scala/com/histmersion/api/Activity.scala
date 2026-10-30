package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Research › Activity: one time-ordered list of what the agents, the server and the person did — agent runs,
  * background jobs, worklog lines and a person's decisions — newest first, each entry naming what it touched (goal, run
  * or job, source, article, list item, decision). `GET /api/activity`, the `activity` tool and `histmersion activity`
  * answer the same pages.
  */
object Activity:

  /** What an entry is. */
  enum EntryKind(val wire: String):
    case Run extends EntryKind("run")
    case Job extends EntryKind("job")
    case Log extends EntryKind("log")
    case Decision extends EntryKind("decision")

  object EntryKind:
    def parse(s: String): Option[EntryKind] = values.find(_.wire == s.trim.toLowerCase)

  /** Who wrote a worklog line: an agent run or a background job (its `job`); a line with neither is a person's, or the
    * server's own bookkeeping.
    */
  enum Writer(val wire: String):
    case Agent extends Writer("agent")
    case Job extends Writer("job")

  /** The list's filters, as the chips name them. */
  enum Show(val wire: String, val label: String):
    case Everything extends Show("all", "Everything")
    case Running extends Show("running", "Running now")
    case Agents extends Show("agents", "Agent work")
    case Jobs extends Show("jobs", "Background jobs")
    case Yours extends Show("yours", "Your actions")
    case Problems extends Show("problems", "Problems")

  object Show:
    def parse(s: String): Option[Show] =
      val t = s.trim.toLowerCase
      values.find(v => v.wire == t || v.toString.toLowerCase == t)

  /** One entry. `key` (`run:<id>`, `job:<id>`, `log:<id>`, `decision:<id>`) is unique and orders entries of the same
    * instant; `at` is when it started (a run, a job), was written (a line) or was decided.
    *
    * `state`: a run's or job's status, a decision's outcome (approved / rejected / failed), empty for a line. `what`: a
    * job's kind, a run's pipeline, a decision's kind. `text`: the line itself, a job's progress or a decision's
    * subject. `job`: the run or job the entry is (run, job), that wrote it (line) or that proposed it (decision); `by` =
    * who wrote a line ([[Writer]]). `queueItem` / `queueList`: the list item it is or ticks off. `endedAt`: when a run
    * or job that is no longer going ended.
    */
  final case class Entry(
      key: String,
      kind: String,
      at: String,
      state: String = "",
      what: String = "",
      text: String = "",
      error: Option[String] = None,
      endedAt: Option[String] = None,
      goal: Option[String] = None,
      job: Option[String] = None,
      by: Option[String] = None,
      citekey: Option[String] = None,
      article: Option[String] = None,
      queueItem: Option[Int] = None,
      queueList: Option[String] = None,
      approval: Option[Long] = None,
      target: Option[String] = None,
      targetKind: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def entryKind: Option[EntryKind] = EntryKind.parse(kind)
  object Entry:
    given Schema[Entry] = summon[KindlingsSchema[Entry]].schema

  /** What the chips count: the runs and jobs running now, and those that failed in the last 7 days. */
  final case class Counts(running: Int = 0, problemsWeek: Int = 0) derives KindlingsCodecAsObject, KindlingsSchema
  object Counts:
    given Schema[Counts] = summon[KindlingsSchema[Counts]].schema

  /** One page, newest first; `next` = the cursor of the next older page (absent on the last). */
  final case class Page(entries: List[Entry], next: Option[String] = None, counts: Counts = Counts())
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object Page:
    given Schema[Page] = summon[KindlingsSchema[Page]].schema

  /** The most entries one page holds, and how many when none is asked for. */
  val MaxLimit = 200
  val DefaultLimit = 30

  /** `kind` = the entry kind of its key. Pure. */
  def keyOf(kind: EntryKind, id: String): String = s"${kind.wire}:$id"
