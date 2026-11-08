package com.histmersion
package api.exchange
import com.histmersion.api.WireEnumValues
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

import com.histmersion.api.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class MergeDecision(kind: MergeDecision.Kind, argument: Option[MergeDecision.Argument] = None):
  def wire: MergeDecision.Wire = MergeDecision.Wire(MergeDecision.Kind.wireOf(kind).value + argument.fold("")(a => s":${a.value}"))

object MergeDecision:
  type Problem = Problem.T
  object Problem extends RefinedType[String, Pure]
  type Wire = Wire.T
  object Wire extends RefinedType[String, Pure]


  type Argument = Argument.T
  object Argument extends RefinedType[String, Pure]

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
    object Wire extends RefinedType[String, Pure]

    def wireOf(k: Kind): Wire = k match
      case KeepLeft       => Wire("keep-left")
      case KeepRight      => Wire("keep-right")
      case Both           => Wire("both")
      case Concat         => Wire("concat")
      case Hull           => Wire("hull")
      case Map            => Wire("map")
      case RenameRight    => Wire("rename-right")
      case RenameLeft     => Wire("rename-left")
      case File           => Wire("file")
      case Merge          => Wire("merge")
      case Drop           => Wire("drop")
      case KeepUnanchored => Wire("keep-unanchored")

    given WireEnumValues[Kind]:
      def all: Array[Kind] = values
      def wire(e: Kind): WireEnumValues.Wire = WireEnumValues.Wire((wireOf(e)).value)
      def fromWire(s: WireEnumValues.Wire): Option[Kind] = values.find(k => wireOf(k) == Wire((s).value.trim))
  def of(kind: Kind): MergeDecision = MergeDecision(kind, None)
  def withArg(kind: Kind, arg: Argument): MergeDecision = MergeDecision(kind, Some(Argument((arg).value)))

  def parse(raw: Wire): Either[Problem, MergeDecision] =
    val s = (raw).value.trim
    val (k, arg) = s.indexOf(':') match
      case -1 => (s, None)
      case i  => (s.take(i), Some(s.drop(i + 1).trim))
    Kind.values.find(d => Kind.wireOf(d) == Kind.Wire(k)) match
      case None =>
        Left(Problem(s"'$s' is not a decision (${Kind.values.map(Kind.wireOf).mkString(", ")})"))
      case Some(d) if ((d.takesArgument) == (true)) && arg.forall(_.isEmpty) =>
        Left(Problem(s"'$s': ${Kind.wireOf(d)} needs an argument (${Kind.wireOf(d)}:<value>)"))
      case Some(d) if !((d.takesArgument) == (true)) && arg.isDefined =>
        Left(Problem(s"'$s': ${Kind.wireOf(d)} takes no argument"))
      case Some(d) => Right(MergeDecision(d, (arg.filter(_.nonEmpty)).map(refactorValue => Argument(refactorValue))))

  given Encoder[MergeDecision] = Encoder.encodeString.contramap((refactorCallback => (refactorCallback.wire).value))
  given Decoder[MergeDecision] = Decoder.decodeString.emap(s => parse(Wire(s)).left.map(_.value))
  given Schema[MergeDecision] = Schema.string[MergeDecision]
