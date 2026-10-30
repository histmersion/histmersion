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
