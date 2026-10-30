package com.histmersion.values

/** What a manager drives: `review` (library-wide: articles until their review converges), `enrich` (library-wide:
  * sources until enriched), `seed` (one research area: new articles proposed, approved and compiled), `watch` (one
  * research area: an acquisition sweep and a currency check on its cron).
  */
enum ManagerKind(val wire: String):
  case Review extends ManagerKind("review")
  case Seed extends ManagerKind("seed")
  case Watch extends ManagerKind("watch")
  case Enrich extends ManagerKind("enrich")

object ManagerKind:
  def parse(s: String): Option[ManagerKind] = values.find(_.wire == s.trim.toLowerCase)

/** The person's switch: `paused` starts nothing new (in-flight runs go on unless paused with them), `stopped` cancelled
  * its in-flight runs and items.
  */
enum ManagerState(val wire: String):
  case Active extends ManagerState("active")
  case Paused extends ManagerState("paused")
  case Stopped extends ManagerState("stopped")

object ManagerState:
  def parse(s: String): Option[ManagerState] = values.find(_.wire == s.trim.toLowerCase)

enum ItemStatus(val wire: String):
  case Pending extends ItemStatus("pending")
  case Held extends ItemStatus("held")
  case Active extends ItemStatus("active")
  case Done extends ItemStatus("done")
  case Stopped extends ItemStatus("stopped")
  case Failed extends ItemStatus("failed")
  case Cancelled extends ItemStatus("cancelled")

  /** Settled: only a person (re-open, enqueue) brings it back. */
  def isFinal: Boolean = this == Done || this == Stopped || this == Failed || this == Cancelled

object ItemStatus:
  def parse(s: String): Option[ItemStatus] = values.find(_.wire == s.trim.toLowerCase)
