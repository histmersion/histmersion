package com.histmersion.domain.automation

object Manager:

  /** What a manager drives: `review` (library-wide: articles until their review converges), `enrich` (library-wide:
    * sources until enriched), `seed` (one research area: new articles proposed, approved and compiled), `watch` (one
    * research area: an acquisition sweep and a currency check on its cron).
    */
  enum Kind(val wire: String):
    case Review extends Kind("review")
    case Seed extends Kind("seed")
    case Watch extends Kind("watch")
    case Enrich extends Kind("enrich")

  object Kind:
    def parse(s: String): Option[Kind] = values.find(_.wire == s.trim.toLowerCase)

  /** The person's switch: `paused` starts nothing new (in-flight runs go on unless paused with them), `stopped`
    * cancelled its in-flight runs and items.
    */
  enum State(val wire: String):
    case Active extends State("active")
    case Paused extends State("paused")
    case Stopped extends State("stopped")

  object State:
    def parse(s: String): Option[State] = values.find(_.wire == s.trim.toLowerCase)
