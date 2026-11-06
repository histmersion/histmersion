// Chimney 2.1 keeps its macro-extension API private[chimney]; only code under io.scalaland.chimney can implement it.
package io.scalaland.chimney.histmersion

import io.github.iltotore.iron.{Constraint, RefinedType}
import io.github.iltotore.iron.constraint.any.Pure
import io.scalaland.chimney.partial

import scala.quoted.*

object IronNewtypePairs:
  type WithoutPackageObjectsResult = WithoutPackageObjectsResult.T
  object WithoutPackageObjectsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithoutPackageObjectsName = WithoutPackageObjectsName.T
  object WithoutPackageObjectsName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JudgesNothingResult = JudgesNothingResult.T
  object JudgesNothingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DifferentConstraintsMessageResult = DifferentConstraintsMessageResult.T
  object DifferentConstraintsMessageResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def withoutPackageObjects(name: io.scalaland.chimney.histmersion.IronNewtypePairs.WithoutPackageObjectsName): io.scalaland.chimney.histmersion.IronNewtypePairs.WithoutPackageObjectsResult =
    name.replaceAll("""\w+\$package\$?\.""", "").replace("scala.Predef.", "")

final class IronNewtypePairs(using val quotes: Quotes):
  import IronNewtypePairs.withoutPackageObjects
  import quotes.reflect.*

  final case class IronNewtype(companion: Symbol, base: TypeRepr, constraint: TypeRepr):
    def name: io.scalaland.chimney.histmersion.IronNewtypePairs.IronNewtype.NameResult = companion.name
    def fullName: io.scalaland.chimney.histmersion.IronNewtypePairs.IronNewtype.FullNameResult = withoutPackageObjects(_root_.io.scalaland.chimney.histmersion.IronNewtypePairs.WithoutPackageObjectsName(companion.fullName))
    def refinedTypeShown: io.scalaland.chimney.histmersion.IronNewtypePairs.IronNewtype.RefinedTypeShownResult =
      s"${withoutPackageObjects(_root_.io.scalaland.chimney.histmersion.IronNewtypePairs.WithoutPackageObjectsName(base.show))} :| ${withoutPackageObjects(_root_.io.scalaland.chimney.histmersion.IronNewtypePairs.WithoutPackageObjectsName(constraint.show))}"

  object IronNewtype:
    type NameResult = NameResult.T
    object NameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FullNameResult = FullNameResult.T
    object FullNameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RefinedTypeShownResult = RefinedTypeShownResult.T
    object RefinedTypeShownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum NewtypePair:
    case SameRefinedType(from: IronNewtype, to: IronNewtype)
    case DifferentConstraints(from: IronNewtype, to: IronNewtype)
    case NotSameNamedNewtypes

  private val refinedTypeClass = TypeRepr.of[RefinedType].typeSymbol

  def ironNewtype(tpe: TypeRepr): Option[IronNewtype] = tpe.dealias match
    case newtypeRef: TypeRef if newtypeRef.typeSymbol.name == "T" =>
      newtypeRef.qualifier match
        case companionRef: TermRef if companionRef.termSymbol.flags.is(Flags.Module) =>
          companionRef.baseType(refinedTypeClass) match
            case AppliedType(_, List(base, constraint)) => Some(IronNewtype(companionRef.termSymbol, base, constraint))
            case _                                      => None
        case _ => None
    case _ => None

  def pair[From: Type, To: Type]: NewtypePair = (ironNewtype(TypeRepr.of[From]), ironNewtype(TypeRepr.of[To])) match
    case (Some(fromNewtype), Some(toNewtype))
        if fromNewtype.companion != toNewtype.companion && fromNewtype.name == toNewtype.name
          && fromNewtype.base =:= toNewtype.base =>
      if fromNewtype.constraint =:= toNewtype.constraint then NewtypePair.SameRefinedType(fromNewtype, toNewtype)
      else NewtypePair.DifferentConstraints(fromNewtype, toNewtype)
    case _ => NewtypePair.NotSameNamedNewtypes

  // Iron's `apply` emits no check when its argument is already refined with the companion's constraint.
  def rewrap[To: Type](from: IronNewtype, to: IronNewtype, source: Expr[?]): Option[Expr[To]] =
    val refinedValue = Select.unique(Ref(from.companion), "value").appliedTo(source.asTerm)
    Implicits.search(TypeRepr.of[Constraint].appliedTo(List(to.base, to.constraint))) match
      case constraint: ImplicitSearchSuccess =>
        Some(
          Select
            .unique(Ref(to.companion), "apply")
            .appliedToType(refinedValue.tpe.widen)
            .appliedTo(refinedValue)
            .appliedTo(constraint.tree)
            .asExprOf[To]
        )
      case _: ImplicitSearchFailure => None

  /** A newtype that judges nothing (`Pure`): what a client layer's copy is. */
  def judgesNothing(n: IronNewtype): io.scalaland.chimney.histmersion.IronNewtypePairs.JudgesNothingResult = _root_.io.scalaland.chimney.histmersion.IronNewtypePairs.JudgesNothingResult(n.constraint =:= TypeRepr.of[Pure])

  private def baseValue(from: IronNewtype, source: Expr[?]): Term =
    Select.unique(Ref(from.companion), "value").appliedTo(source.asTerm)

  /** Into a newtype that judges nothing: the base value, wrapped without a check. */
  def unchecked[To: Type](from: IronNewtype, to: IronNewtype, source: Expr[?]): Expr[To] =
    Select.unique(Ref(to.companion), "assume").appliedTo(baseValue(from, source)).asExprOf[To]

  /** Into a newtype with a constraint of its own: the base value checked by the target's constraint, a failure the
    * target's reason.
    */
  def checked[To: Type](from: IronNewtype, to: IronNewtype, source: Expr[?]): Expr[partial.Result[To]] =
    val either =
      Select.unique(Ref(to.companion), "either").appliedTo(baseValue(from, source)).asExprOf[Either[String, To]]
    '{ partial.Result.fromEitherString($either) }

  def differentConstraintsMessage(from: IronNewtype, to: IronNewtype): io.scalaland.chimney.histmersion.IronNewtypePairs.DifferentConstraintsMessageResult =
    s"""${from.fullName} and ${to.fullName} refine the same base type with different constraints:
       |  from: ${from.refinedTypeShown}
       |  to:   ${to.refinedTypeShown}
       |Only newtypes with the same constraint, or into one that judges nothing (Pure), convert without a check; into a
       |constraint of its own, convert with transformIntoPartial (the target's constraint checks the value), or give the
       |pair a Transformer.""".stripMargin
