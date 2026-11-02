package com.histmersion.api.exchange
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

import com.histmersion.util.data.exchange.DecisionKind
import com.histmersion.api.given

final case class MergeDecision(kind: DecisionKind, argument: Option[String] = None):
  def wire: String = DecisionKind.wireOf(kind) + argument.fold("")(a => s":$a")

object MergeDecision:
  def of(kind: DecisionKind): MergeDecision = MergeDecision(kind, None)
  def withArg(kind: DecisionKind, arg: String): MergeDecision = MergeDecision(kind, Some(arg))

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

  given Encoder[MergeDecision] = Encoder.encodeString.contramap(_.wire)
  given Decoder[MergeDecision] = Decoder.decodeString.emap(parse)
  given Schema[MergeDecision] = Schema.string[MergeDecision]
