package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The web UI's toolbar notices: how many items each list holds that wait on someone (open approvals, open
  * acquisitions, the open backlog), and which windows have something NEW since the reader last opened them — those
  * buttons are drawn bold until the window is opened.
  *
  *   - a BOARD is a window the toolbar watches: its wire key is the window's own (`goals`, `tasks`, …), so the page
  *     maps one to the other without a table;
  *   - its `latest` is when the newest of its items was created or changed, its `seen` the `latest` the reader's open
  *     window last showed (stored server-side, table `ui_seen`, so every browser and device agrees). Something is new
  *     when `latest` is after `seen`;
  *   - a deleted item is not news: only rows that still exist move `latest`.
  *
  * Both instants are ISO-8601 UTC strings in ONE fixed format (`YYYY-MM-DDTHH:MM:SS.ffffffZ`, what the store renders),
  * so they compare as strings. Adding a board is an enum case here and its `latest`/`count` query in the store
  * (`UiRepo`).
  */
object UiNotices:

  /** A watched window. `counted`: its button carries a count (items that wait on someone). */
  enum Board(val wire: String, val counted: Boolean):
    case Goals extends Board("goals", counted = false)
    case Tasks extends Board("tasks", counted = true)
    case Approvals extends Board("approvals", counted = true)
    case Acquire extends Board("acquire", counted = true)
    case Log extends Board("log", counted = false)

  object Board:
    def parse(s: String): Option[Board] = values.find(_.wire == Option(s).map(_.trim.toLowerCase).getOrElse(""))
    def wires: List[String] = values.toList.map(_.wire)

  /** One board on the wire: its key, the count (counted boards only), when its newest item changed, when the reader
    * last saw it, and whether something is new (`latest` after `seen`).
    */
  final case class Counter(
      key: String,
      count: Option[Int] = None,
      latest: Option[String] = None,
      seen: Option[String] = None,
      fresh: Boolean = false
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object Counter:
    given Schema[Counter] = summon[KindlingsSchema[Counter]].schema

  /** The research agents at a glance, for the top bar's status: how many runs are running and how many wait their turn,
    * whether the autopilot is on, and whether it is finishing current work before it pauses (draining).
    */
  final case class AgentsGlance(running: Int = 0, queued: Int = 0, on: Boolean = false, draining: Boolean = false)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object AgentsGlance:
    given Schema[AgentsGlance] = summon[KindlingsSchema[AgentsGlance]].schema

  /** `GET /api/ui/counters` — every board, in the enum's order; `agents` only where the server runs research agents.
    */
  final case class Counters(boards: List[Counter], agents: Option[AgentsGlance] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object Counters:
    given Schema[Counters] = summon[KindlingsSchema[Counters]].schema

  /** `POST /api/ui/seen/{board}` — the reader opened the window, which showed items up to `at` (the `latest` it was
    * given). Absent: up to now. The marker only moves forward.
    */
  final case class SeenMark(at: Option[String] = None) derives KindlingsCodecAsObject, KindlingsSchema
  object SeenMark:
    given Schema[SeenMark] = summon[KindlingsSchema[SeenMark]].schema

  /** Pure: is there something new? Nothing at all is never new; never seen with something there is. */
  def fresh(latest: Option[String], seen: Option[String]): Boolean =
    latest.exists(l => seen.forall(s => l > s))

  /** Pure: the counter of one board from what the store read. */
  def counter(board: Board, count: Option[Int], latest: Option[String], seen: Option[String]): Counter =
    Counter(board.wire, count.filter(_ => board.counted), latest, seen, fresh(latest, seen))

  /** Pure: the marker after the reader saw `at` — never earlier than the one held. */
  def advance(held: Option[String], at: String): String = held.filter(_ > at).getOrElse(at)

  /** Pure: what a count reads as on a button — nothing for zero, `99+` past 99. */
  def badge(count: Option[Int]): Option[String] =
    count.filter(_ > 0).map(n => if n > 99 then "99+" else n.toString)
