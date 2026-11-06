package com.histmersion.api.automation

object Manager:

  /** What a manager drives: `review` (library-wide: articles until their review converges), `enrich` (library-wide:
    * sources until enriched), `seed` (one research area: new articles proposed, approved and compiled), `watch` (one
    * research area: an acquisition sweep and a currency check on its cron).
    */
  enum Kind(val wire: Kind.Wire):
    case Review extends Kind(_root_.com.histmersion.api.automation.Manager.Kind.Wire("review"))
    case Seed extends Kind(_root_.com.histmersion.api.automation.Manager.Kind.Wire("seed"))
    case Watch extends Kind(_root_.com.histmersion.api.automation.Manager.Kind.Wire("watch"))
    case Enrich extends Kind(_root_.com.histmersion.api.automation.Manager.Kind.Wire("enrich"))

  object Kind:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.automation.Manager.Kind.ParseS): Option[Kind] = values.find(_.wire == (s).value.trim.toLowerCase)

  /** The person's switch: `paused` starts nothing new (in-flight runs go on unless paused with them), `stopped`
    * cancelled its in-flight runs and items.
    */
  enum State(val wire: State.Wire):
    case Active extends State(_root_.com.histmersion.api.automation.Manager.State.Wire("active"))
    case Paused extends State(_root_.com.histmersion.api.automation.Manager.State.Wire("paused"))
    case Stopped extends State(_root_.com.histmersion.api.automation.Manager.State.Wire("stopped"))

  object State:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.automation.Manager.State.ParseS): Option[State] = values.find(_.wire == (s).value.trim.toLowerCase)
