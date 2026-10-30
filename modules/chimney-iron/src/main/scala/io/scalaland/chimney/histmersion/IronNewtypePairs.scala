// Chimney 2.1 keeps its macro-extension API private[chimney]; only code under io.scalaland.chimney can implement it.
package io.scalaland.chimney.histmersion

import io.github.iltotore.iron.{Constraint, RefinedType}

import scala.quoted.*

object IronNewtypePairs:
  def withoutPackageObjects(name: String): String =
    name.replaceAll("""\w+\$package\$?\.""", "").replace("scala.Predef.", "")

final class IronNewtypePairs(using val quotes: Quotes):
  import IronNewtypePairs.withoutPackageObjects
  import quotes.reflect.*

  final case class IronNewtype(companion: Symbol, base: TypeRepr, constraint: TypeRepr):
    def name: String = companion.name
    def fullName: String = withoutPackageObjects(companion.fullName)
    def refinedTypeShown: String =
      s"${withoutPackageObjects(base.show)} :| ${withoutPackageObjects(constraint.show)}"

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

  def differentConstraintsMessage(from: IronNewtype, to: IronNewtype): String =
    s"""${from.fullName} and ${to.fullName} refine the same base type with different constraints:
       |  from: ${from.refinedTypeShown}
       |  to:   ${to.refinedTypeShown}
       |Only newtypes with the same constraint convert without a check; give the pair a Transformer or PartialTransformer.""".stripMargin
