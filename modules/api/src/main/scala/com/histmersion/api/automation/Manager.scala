package com.histmersion
package api.automation

import io.github.iltotore.iron.constraint.any.Pure

object Manager:

  /** What a manager drives: `review` (library-wide: articles until their review converges), `enrich` (library-wide:
    * sources until enriched), `seed` (one research area: new articles proposed, approved and compiled), `watch` (one
    * research area: an acquisition sweep and a currency check on its cron).
    */
  enum Kind(val wire: Kind.Wire) derives CanEqual:
    case Review extends Kind(Kind.Wire("review"))
    case Seed extends Kind(Kind.Wire("seed"))
    case Watch extends Kind(Kind.Wire("watch"))
    case Enrich extends Kind(Kind.Wire("enrich"))

  object Kind:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def parse(s: Wire): Option[Kind] = values.find(_.wire == Wire((s).value.trim.toLowerCase))

  /** The person's switch: `paused` starts nothing new (in-flight runs go on unless paused with them), `stopped`
    * cancelled its in-flight runs and items.
    */
  enum State(val wire: State.Wire) derives CanEqual:
    case Active extends State(State.Wire("active"))
    case Paused extends State(State.Wire("paused"))
    case Stopped extends State(State.Wire("stopped"))

  object State:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def parse(s: Wire): Option[State] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
