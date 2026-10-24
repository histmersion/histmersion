package io.scalaland.chimney.histmersion

import hearth.MacroCommons
import hearth.fp.effect.MIO
import io.scalaland.chimney.integrations.ChimneyMacroExtension
import io.scalaland.chimney.internal.compiletime.DerivationError
import io.scalaland.chimney.internal.compiletime.derivation.transformer.ChimneyEngineExtensionApi

final class IronNewtypeToNewtypeExtension extends ChimneyMacroExtension:

  override def extend(engine: MacroCommons & ChimneyEngineExtensionApi): Unit =
    import engine.*

    // Hearth's Type and Expr are scala.quoted's own on Scala 3, the only platform this module builds for.
    def quotedType[A](tpe: Type[A]): scala.quoted.Type[A] = tpe.asInstanceOf[scala.quoted.Type[A]]
    def quotedExpr[A](expr: Expr[A]): scala.quoted.Expr[A] = expr.asInstanceOf[scala.quoted.Expr[A]]
    def hearthExpr[A](expr: scala.quoted.Expr[A]): Expr[A] = expr.asInstanceOf[Expr[A]]

    def rewrapNewtype[From, To](using From: Type[From], To: Type[To]): SpecialCasedTransformation[From, To] =
      new SpecialCasedTransformation[From, To]:
        override def specialCase(using context: SpecialCaseContext[From, To]): MIO[Option[DerivedExpr[To]]] =
          withQuotes { quotes ?=>
            val pairs: IronNewtypePairs = IronNewtypePairs(using quotes)
            given scala.quoted.Type[From] = quotedType(From)
            given scala.quoted.Type[To]   = quotedType(To)
            pairs.pair[From, To] match
              case pairs.NewtypePair.SameRefinedType(from, to) =>
                pairs.rewrap[To](from, to, quotedExpr(sourceOf(using context))) match
                  case Some(rewrapped) => specialCasedTotal(hearthExpr(rewrapped))
                  case None            => specialCaseYield[To]
              case pairs.NewtypePair.DifferentConstraints(from, to) =>
                MIO.fail(DerivationError.PolicyViolation(pairs.differentConstraintsMessage(from, to)))
              case pairs.NewtypePair.NotSameNamedNewtypes => specialCaseYield[To]
          }

    registerSpecialCase(new SpecialCaseHandler:
      override def apply[From, To](using From: Type[From], To: Type[To]): Option[SpecialCasedTransformation[From, To]] =
        withQuotes { quotes ?=>
          val pairs: IronNewtypePairs = IronNewtypePairs(using quotes)
          given scala.quoted.Type[From] = quotedType(From)
          given scala.quoted.Type[To]   = quotedType(To)
          pairs.pair[From, To] match
            case pairs.NewtypePair.NotSameNamedNewtypes => None
            case _                                      => Some(rewrapNewtype[From, To])
        }
    )
