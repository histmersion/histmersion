package com.histmersion.api.exchange
import com.histmersion.api.WireEnumValues
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

import com.histmersion.api.given

final case class MergeDecision(kind: MergeDecision.Kind, argument: Option[String] = None):
  def wire: String = MergeDecision.Kind.wireOf(kind) + argument.fold("")(a => s":$a")

object MergeDecision:

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
      case Map | RenameRight | RenameLeft | File => true
      case _                                     => false

  object Kind:
    def wireOf(k: Kind): String = k match
      case KeepLeft       => "keep-left"
      case KeepRight      => "keep-right"
      case Both           => "both"
      case Concat         => "concat"
      case Hull           => "hull"
      case Map            => "map"
      case RenameRight    => "rename-right"
      case RenameLeft     => "rename-left"
      case File           => "file"
      case Merge          => "merge"
      case Drop           => "drop"
      case KeepUnanchored => "keep-unanchored"

    given WireEnumValues[Kind]:
      def all: Array[Kind] = Kind.values
      def wire(e: Kind): String = wireOf(e)
      def fromWire(s: String): Option[Kind] = Kind.values.find(k => wireOf(k) == s.trim)
  def of(kind: MergeDecision.Kind): MergeDecision = MergeDecision(kind, None)
  def withArg(kind: MergeDecision.Kind, arg: String): MergeDecision = MergeDecision(kind, Some(arg))

  def parse(raw: String): Either[String, MergeDecision] =
    val s = raw.trim
    val (k, arg) = s.indexOf(':') match
      case -1 => (s, None)
      case i  => (s.take(i), Some(s.drop(i + 1).trim))
    MergeDecision.Kind.values.find(d => MergeDecision.Kind.wireOf(d) == k) match
      case None =>
        Left(s"'$s' is not a decision (${MergeDecision.Kind.values.map(MergeDecision.Kind.wireOf).mkString(", ")})")
      case Some(d) if d.takesArgument && arg.forall(_.isEmpty) =>
        Left(s"'$s': ${MergeDecision.Kind.wireOf(d)} needs an argument (${MergeDecision.Kind.wireOf(d)}:<value>)")
      case Some(d) if !d.takesArgument && arg.isDefined =>
        Left(s"'$s': ${MergeDecision.Kind.wireOf(d)} takes no argument")
      case Some(d) => Right(MergeDecision(d, arg.filter(_.nonEmpty)))

  given Encoder[MergeDecision] = Encoder.encodeString.contramap(_.wire)
  given Decoder[MergeDecision] = Decoder.decodeString.emap(parse)
  given Schema[MergeDecision] = Schema.string[MergeDecision]
