package com.histmersion.api.exchange
import com.histmersion.api.WireEnumValues
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

import com.histmersion.api.given

final case class MergeDecision(kind: MergeDecision.Kind, argument: Option[MergeDecision.Argument] = None):
  def wire: com.histmersion.api.exchange.MergeDecision.WireResult = MergeDecision.Kind.wireOf(kind) + argument.fold("")(a => s":$a")

object MergeDecision:

  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithArgArg = WithArgArg.T
  object WithArgArg extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseResultPart1 = ParseResultPart1.T
  object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseRaw = ParseRaw.T
  object ParseRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
    def takesArgument: com.histmersion.api.exchange.MergeDecision.Kind.TakesArgumentResult = this match
      case Map | RenameRight | RenameLeft | File => _root_.com.histmersion.api.exchange.MergeDecision.Kind.TakesArgumentResult(true)
      case _                                     => _root_.com.histmersion.api.exchange.MergeDecision.Kind.TakesArgumentResult(false)

  object Kind:
    type TakesArgumentResult = TakesArgumentResult.T
    object TakesArgumentResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WireOfResult = WireOfResult.T
    object WireOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromWireS = FromWireS.T
    object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def wireOf(k: Kind): com.histmersion.api.exchange.MergeDecision.Kind.WireOfResult = k match
      case KeepLeft       => _root_.com.histmersion.api.exchange.MergeDecision.Kind.WireOfResult("keep-left")
      case KeepRight      => _root_.com.histmersion.api.exchange.MergeDecision.Kind.WireOfResult("keep-right")
      case Both           => _root_.com.histmersion.api.exchange.MergeDecision.Kind.WireOfResult("both")
      case Concat         => _root_.com.histmersion.api.exchange.MergeDecision.Kind.WireOfResult("concat")
      case Hull           => _root_.com.histmersion.api.exchange.MergeDecision.Kind.WireOfResult("hull")
      case Map            => _root_.com.histmersion.api.exchange.MergeDecision.Kind.WireOfResult("map")
      case RenameRight    => _root_.com.histmersion.api.exchange.MergeDecision.Kind.WireOfResult("rename-right")
      case RenameLeft     => _root_.com.histmersion.api.exchange.MergeDecision.Kind.WireOfResult("rename-left")
      case File           => _root_.com.histmersion.api.exchange.MergeDecision.Kind.WireOfResult("file")
      case Merge          => _root_.com.histmersion.api.exchange.MergeDecision.Kind.WireOfResult("merge")
      case Drop           => _root_.com.histmersion.api.exchange.MergeDecision.Kind.WireOfResult("drop")
      case KeepUnanchored => _root_.com.histmersion.api.exchange.MergeDecision.Kind.WireOfResult("keep-unanchored")

    given WireEnumValues[Kind]:
      def all: Array[Kind] = Kind.values
      def wire(e: Kind): com.histmersion.api.exchange.MergeDecision.Kind.WireResult = wireOf(e)
      def fromWire(s: com.histmersion.api.exchange.MergeDecision.Kind.FromWireS): Option[Kind] = Kind.values.find(k => wireOf(k) == _root_.com.histmersion.api.exchange.MergeDecision.Kind.WireOfResult((s).value.trim))
  def of(kind: MergeDecision.Kind): MergeDecision = MergeDecision(kind, None)
  def withArg(kind: MergeDecision.Kind, arg: com.histmersion.api.exchange.MergeDecision.WithArgArg): MergeDecision = MergeDecision(kind, Some(arg))

  def parse(raw: com.histmersion.api.exchange.MergeDecision.ParseRaw): Either[com.histmersion.api.exchange.MergeDecision.ParseResultPart1, MergeDecision] =
    val s = (raw).value.trim
    val (k, arg) = s.indexOf(':') match
      case -1 => (s, None)
      case i  => (s.take(i), Some(s.drop(i + 1).trim))
    MergeDecision.Kind.values.find(d => MergeDecision.Kind.wireOf(d) == _root_.com.histmersion.api.exchange.MergeDecision.Kind.WireOfResult(k)) match
      case None =>
        Left(_root_.com.histmersion.api.exchange.MergeDecision.ParseResultPart1(s"'$s' is not a decision (${MergeDecision.Kind.values.map(MergeDecision.Kind.wireOf).mkString(", ")})"))
      case Some(d) if d.takesArgument && arg.forall(_.isEmpty) =>
        Left(_root_.com.histmersion.api.exchange.MergeDecision.ParseResultPart1(s"'$s': ${MergeDecision.Kind.wireOf(d)} needs an argument (${MergeDecision.Kind.wireOf(d)}:<value>)"))
      case Some(d) if !d.takesArgument && arg.isDefined =>
        Left(_root_.com.histmersion.api.exchange.MergeDecision.ParseResultPart1(s"'$s': ${MergeDecision.Kind.wireOf(d)} takes no argument"))
      case Some(d) => Right(MergeDecision(d, (arg.filter(_.nonEmpty)).map(refactorValue => _root_.com.histmersion.api.exchange.MergeDecision.Argument(refactorValue))))

  given Encoder[MergeDecision] = Encoder.encodeString.contramap((_.wire).value)
  given Decoder[MergeDecision] = Decoder.decodeString.emap(_root_.com.histmersion.api.exchange.MergeDecision.ParseRaw(parse))
  given Schema[MergeDecision] = Schema.string[MergeDecision]
