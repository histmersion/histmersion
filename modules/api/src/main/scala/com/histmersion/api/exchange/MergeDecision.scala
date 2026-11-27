package com.histmersion
package api.exchange
import com.histmersion.api.json.NewtypeCodecs.given
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.histmersion.util.json.JsonCodec

import com.histmersion.util.problem.Text.sentence
import com.histmersion.util.problem.Problem

import com.histmersion.api.WireEnumValues
import sttp.tapir.{Schema, Validator}

import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

final case class MergeDecision(kind: MergeDecision.Kind, argument: Option[MergeDecision.Argument] = None):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def wire: MergeDecision.Wire =
    MergeDecision.Wire(MergeDecision.Kind.wireOf(kind).value + argument.fold("")(a => s":${a.value}"))

object MergeDecision:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  type Argument = Argument.T
  object Argument extends Newtype[String, Pure]

  enum Kind derives CanEqual:
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
    object Wire extends Newtype[String, Pure]

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
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def wire(e: Kind): WireEnumValues.Wire = WireEnumValues.Wire((wireOf(e)).value)
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def fromWire(s: WireEnumValues.Wire): Option[Kind] = values.find(k => wireOf(k) == Wire((s).value.trim))
  def of(kind: Kind): MergeDecision = MergeDecision(kind, None)
  def withArg(kind: Kind, arg: Argument): MergeDecision = MergeDecision(kind, Some(arg))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(raw: Wire): Either[Problem, MergeDecision] =
    val s = (raw).value.trim
    val (k, arg) = s.indexOf(':') match
      case -1 => (s, None)
      case i  => (s.take(i), Some(s.drop(i + 1).trim))
    Kind.values.find(d => Kind.wireOf(d) == Kind.Wire(k)) match
      case None =>
        Left(
          Problem.Invalid.Unknown.here(
            Problem.TypeName.named[Kind],
            Problem.Raw(s"$s"),
            Kind.values.toVector.map(k => Problem.Raw(s"${Kind.wireOf(k)}"))
          )
        )
      case Some(d) if ((d.takesArgument) == (true)) && arg.forall(_.isEmpty) =>
        Left(
          Problem.Invalid.Missing(Problem.Field("argument"), Vector(Problem.Field(s"$s")))
        )
      case Some(d) if !((d.takesArgument) == (true)) && arg.isDefined =>
        Left(
          Problem.Invalid.Unreadable
            .here(Problem.TypeName.named[Kind], Problem.Raw(s"$s"), Vector(Problem.Raw(s"${Kind.wireOf(d)}")))
        )
      case Some(d) => Right(MergeDecision(d, (arg.filter(_.nonEmpty)).map(refactorValue => Argument(refactorValue))))

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  given JsonValueCodec[MergeDecision] = JsonCodec.mapped[MergeDecision, String](
    (refactorCallback => (refactorCallback.wire).value),
    s => parse(Wire(s)).left.map(_.sentence)
  )
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  given Schema[MergeDecision] = Schema
    .string[MergeDecision]
    .validate(
      Validator
        .pattern[String](Kind.values.map(k => s"${Kind.wireOf(k)}").mkString("^(", "|", ")(:.+)?$"))
        .contramap((d: MergeDecision) => s"${d.wire}")
    )
    .encodedExample(s"${of(Kind.values.head).wire}")
