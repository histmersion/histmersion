package com.histmersion.api

import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

import com.histmersion.values.DecisionKind

/** One decision: its kind and, for the kinds that take one, the argument (a citekey, a path). On the wire and in the
  * resolution file it is one string — `keep-left`, `map:guterbock1997_hittite`.
  */
final case class MergeDecision(kind: DecisionKind, argument: Option[String] = None):
  def wire: String = DecisionKind.wireOf(kind) + argument.fold("")(a => s":$a")

object MergeDecision:
  def of(kind: DecisionKind): MergeDecision = MergeDecision(kind, None)
  def withArg(kind: DecisionKind, arg: String): MergeDecision = MergeDecision(kind, Some(arg))

  /** Parse `kind[:argument]`; the argument is required exactly by the kinds that take one. Pure. */
  def parse(raw: String): Either[String, MergeDecision] =
    val s = raw.trim
    val (k, arg) = s.indexOf(':') match
      case -1 => (s, None)
      case i  => (s.take(i), Some(s.drop(i + 1).trim))
    DecisionKind.values.find(d => DecisionKind.wireOf(d) == k) match
      case None => Left(s"'$s' is not a decision (${DecisionKind.values.map(DecisionKind.wireOf).mkString(", ")})")
      case Some(d) if d.takesArgument && arg.forall(_.isEmpty) =>
        Left(s"'$s': ${DecisionKind.wireOf(d)} needs an argument (${DecisionKind.wireOf(d)}:<value>)")
      case Some(d) if !d.takesArgument && arg.isDefined =>
        Left(s"'$s': ${DecisionKind.wireOf(d)} takes no argument")
      case Some(d) => Right(MergeDecision(d, arg.filter(_.nonEmpty)))

  // the string form IS the value (the resolution file's spelling), so it crosses the wire as one
  given Encoder[MergeDecision] = Encoder.encodeString.contramap(_.wire)
  given Decoder[MergeDecision] = Decoder.decodeString.emap(parse)
  given Schema[MergeDecision] = Schema.string[MergeDecision]
