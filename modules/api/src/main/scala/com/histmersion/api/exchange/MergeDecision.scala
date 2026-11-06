package com.histmersion.api.exchange
import com.histmersion.api.WireEnumValues
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

import com.histmersion.api.given

final case class MergeDecision(kind: MergeDecision.Kind, argument: Option[MergeDecision.Argument] = None):
  def wire: com.histmersion.api.exchange.MergeDecision.Wire = MergeDecision.Kind.wireOf(kind) + argument.fold("")(a => s":$a")

object MergeDecision:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type Argument = Argument.T
  object Argument extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum Kind:
    /** Keep this collection's version. */
    case KeepLeft

    /** Take the bundle's version. */
    case KeepRight

    /** Keep both (two sources; the bundle's article under another slug). */
    case Both

    /** Join both texts (summary, notes). */
    case Concat

    /** The smallest period covering both. */
    case Hull

    /** One source, under the given citekey; the other citekey becomes an alias. */
    case Map

    /** Keep both, the bundle's under a new citekey. */
    case RenameRight

    /** Keep both, this collection's under a new citekey. */
    case RenameLeft

    /** The merged text is in a file (an article). */
    case File

    /** Two records are one: fold the bundle's into this collection's (a near-duplicate finding). */
    case Merge

    /** Leave the bundle's record out (an attestation that cannot be re-anchored). */
    case Drop

    /** Keep the attestation without a position in the text (its quote is kept). */
    case KeepUnanchored

    /** `true` when the decision needs an argument (`map:<citekey>`). */
    def takesArgument: Boolean = this match
      case Map | RenameRight | RenameLeft | File => (true)
      case _                                     => (false)

  object Kind:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def wireOf(k: Kind): com.histmersion.api.exchange.MergeDecision.Kind.Wire = k match
      case KeepLeft       => _root_.com.histmersion.api.exchange.MergeDecision.Kind.Wire("keep-left")
      case KeepRight      => _root_.com.histmersion.api.exchange.MergeDecision.Kind.Wire("keep-right")
      case Both           => _root_.com.histmersion.api.exchange.MergeDecision.Kind.Wire("both")
      case Concat         => _root_.com.histmersion.api.exchange.MergeDecision.Kind.Wire("concat")
      case Hull           => _root_.com.histmersion.api.exchange.MergeDecision.Kind.Wire("hull")
      case Map            => _root_.com.histmersion.api.exchange.MergeDecision.Kind.Wire("map")
      case RenameRight    => _root_.com.histmersion.api.exchange.MergeDecision.Kind.Wire("rename-right")
      case RenameLeft     => _root_.com.histmersion.api.exchange.MergeDecision.Kind.Wire("rename-left")
      case File           => _root_.com.histmersion.api.exchange.MergeDecision.Kind.Wire("file")
      case Merge          => _root_.com.histmersion.api.exchange.MergeDecision.Kind.Wire("merge")
      case Drop           => _root_.com.histmersion.api.exchange.MergeDecision.Kind.Wire("drop")
      case KeepUnanchored => _root_.com.histmersion.api.exchange.MergeDecision.Kind.Wire("keep-unanchored")

    given WireEnumValues[Kind]:
      def all: Array[Kind] = Kind.values
      def wire(e: Kind): _root_.com.histmersion.api.WireEnumValues.Wire = _root_.com.histmersion.api.WireEnumValues.Wire((wireOf(e)).value)
      def fromWire(s: _root_.com.histmersion.api.WireEnumValues.Wire): _root_.scala.Option[Kind] = Kind.values.find(k => wireOf(k) == _root_.com.histmersion.api.exchange.MergeDecision.Kind.Wire((s).value.trim))
  def of(kind: MergeDecision.Kind): MergeDecision = MergeDecision(kind, None)
  def withArg(kind: MergeDecision.Kind, arg: com.histmersion.api.exchange.MergeDecision.Argument): MergeDecision = MergeDecision(kind, Some(_root_.com.histmersion.api.exchange.MergeDecision.Argument((arg).value)))

  def parse(raw: com.histmersion.api.exchange.MergeDecision.Wire): Either[com.histmersion.api.exchange.MergeDecision.Problem, MergeDecision] =
    val s = (raw).value.trim
    val (k, arg) = s.indexOf(':') match
      case -1 => (s, None)
      case i  => (s.take(i), Some(s.drop(i + 1).trim))
    MergeDecision.Kind.values.find(d => MergeDecision.Kind.wireOf(d) == _root_.com.histmersion.api.exchange.MergeDecision.Kind.Wire(k)) match
      case None =>
        Left(_root_.com.histmersion.api.exchange.MergeDecision.Problem(s"'$s' is not a decision (${MergeDecision.Kind.values.map(MergeDecision.Kind.wireOf).mkString(", ")})"))
      case Some(d) if ((d.takesArgument) == (true)) && arg.forall(_.isEmpty) =>
        Left(_root_.com.histmersion.api.exchange.MergeDecision.Problem(s"'$s': ${MergeDecision.Kind.wireOf(d)} needs an argument (${MergeDecision.Kind.wireOf(d)}:<value>)"))
      case Some(d) if !((d.takesArgument) == (true)) && arg.isDefined =>
        Left(_root_.com.histmersion.api.exchange.MergeDecision.Problem(s"'$s': ${MergeDecision.Kind.wireOf(d)} takes no argument"))
      case Some(d) => Right(MergeDecision(d, (arg.filter(_.nonEmpty)).map(refactorValue => _root_.com.histmersion.api.exchange.MergeDecision.Argument(refactorValue))))

  given Encoder[MergeDecision] = Encoder.encodeString.contramap((refactorCallback => (refactorCallback.wire).value))
  given Decoder[MergeDecision] = Decoder.decodeString.emap(_root_.com.histmersion.api.exchange.MergeDecision.Wire(parse))
  given Schema[MergeDecision] = Schema.string[MergeDecision]
