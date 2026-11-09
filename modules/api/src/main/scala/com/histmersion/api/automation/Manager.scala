package com.histmersion
package api.automation

import com.histmersion.api.given
import com.histmersion.api.json.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
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

  /** The status line of a manager: what holds it (`kind`) and the rest of the sentence (`detail`). The line is
    * [[Status.render]]ed from the two and stored as that text; [[Status.parse]] reads every line `render` writes.
    */
  final case class Status(kind: Status.Wire, detail: Status.Detail)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        Schema

  object Status:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    type Detail = Detail.T
    object Detail extends Newtype[String, Pure]:
      def read(text: String): Detail = Detail(text)

    /** What a manager's line is about; the prefix is the text every line of the kind starts with. */
    enum Kind(val wire: Wire, val prefix: String, val exact: Boolean) derives CanEqual:
      case Stopped extends Kind(Wire("stopped"), "stopped", true)
      case Paused extends Kind(Wire("paused"), "paused", false)
      case Budget extends Kind(Wire("budget"), "budget: ", false)
      case Starved extends Kind(Wire("starved"), "starved: ", false)
      case AtCapacity extends Kind(Wire("at-capacity"), "at capacity", false)
      case Idle extends Kind(Wire("idle"), "idle", true)
      case Queued extends Kind(Wire("queued"), "focus:", false)
      case Other extends Kind(Wire("other"), "", false)

      def matches(text: String): Boolean = if exact then text == prefix else text.startsWith(prefix)

    object Kind:
      def parse(s: Wire): Option[Kind] = values.find(_.wire == s)

    def apply(kind: Kind, detail: String = ""): Status = Status(kind.wire, Detail.read(detail))

    extension (s: Status)
      /** The kind of the line; a kind this version does not know reads as [[Kind.Other]]. */
      def is: Kind = Kind.parse(s.kind).getOrElse(Kind.Other)

      /** The line as shown and stored. */
      def render: String = s"${s.is.prefix}${s.detail}"

      /** This line with `suffix` added, read back as a line (the kind may change with the text). */
      def appended(suffix: String): Status = parse(s.render + suffix)

    /** Reads a stored line: the first kind whose prefix it starts with, [[Kind.Other]] for any text no writer produces. */
    def parse(text: String): Status =
      val kind = Kind.values.find(k => k != Kind.Other && k.matches(text)).getOrElse(Kind.Other)
      Status(kind.wire, Detail.read(text.drop(kind.prefix.length)))
