package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

object Activity:

  enum EntryKind(val wire: String):
    case Run extends EntryKind("run")
    case Job extends EntryKind("job")
    case Log extends EntryKind("log")
    case Decision extends EntryKind("decision")

    /** The entry's kind as the activity list names it. */
    def wording: String = this match
      case Run      => "Agent work"
      case Job      => "Background job"
      case Log      => "Note"
      case Decision => "You decided"

  object EntryKind:
    def parse(s: String): Option[EntryKind] = values.find(_.wire == s.trim.toLowerCase)

  enum Writer(val wire: String):
    case Agent extends Writer("agent")
    case Job extends Writer("job")

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

  final case class Counts(running: Int = 0, problemsWeek: Int = 0) derives KindlingsCodecAsObject, KindlingsSchema
  object Counts:
    given Schema[Counts] = summon[KindlingsSchema[Counts]].schema

  final case class Page(entries: List[Entry], next: Option[String] = None, counts: Counts = Counts())
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object Page:
    given Schema[Page] = summon[KindlingsSchema[Page]].schema

  val MaxLimit = 200
  val DefaultLimit = 30

  def keyOf(kind: EntryKind, id: String): String = s"${kind.wire}:$id"
