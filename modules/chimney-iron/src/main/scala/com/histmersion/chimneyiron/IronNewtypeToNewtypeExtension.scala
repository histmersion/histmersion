package com.histmersion
package chimneyiron

import hearth.{MacroCommons, MacroCommonsScala3}
import hearth.fp.effect.MIO
import io.scalaland.chimney.integrations.ChimneyMacroExtension
import io.scalaland.chimney.internal.compiletime.DerivationError
import io.scalaland.chimney.internal.compiletime.derivation.transformer.ChimneyEngineExtensionApi

final class IronNewtypeToNewtypeExtension extends ChimneyMacroExtension:

  // Scala 3 is the only platform this module builds for: there Hearth's Type and Expr are scala.quoted's own.
  override def extend(engine: MacroCommons & ChimneyEngineExtensionApi): Unit = engine match
    case MacroCommonsScala3(scala3) => extendScala3(scala3)
    case _                          => ()

  private def extendScala3(engine: MacroCommonsScala3 & ChimneyEngineExtensionApi): Unit =
    import engine.*

    def rewrapNewtype[From, To](using From: Type[From], To: Type[To]): SpecialCasedTransformation[From, To] =
      new SpecialCasedTransformation[From, To]:
        override def specialCase(using context: SpecialCaseContext[From, To]): MIO[Option[DerivedExpr[To]]] =
          withQuotes { quotes ?=>
            val pairs: IronNewtypePairs = IronNewtypePairs(using quotes)
            pairs.pair[From, To] match
              case pairs.NewtypePair.SameRefinedType(from, to) =>
                pairs.rewrap[To](from, to, sourceOf(using context)) match
                  case Some(rewrapped) => specialCasedTotal(rewrapped)
                  case None            => specialCaseYield[To]
              case pairs.NewtypePair.DifferentConstraints(from, to) if pairs.judgesNothing(to) =>
                specialCasedTotal(pairs.unchecked[To](from, to, sourceOf(using context)))
              case pairs.NewtypePair.DifferentConstraints(from, to) if isPartialContext(using context) =>
                specialCasedPartial(pairs.checked[To](from, to, sourceOf(using context)))
              case pairs.NewtypePair.DifferentConstraints(from, to) =>
                MIO.fail(DerivationError.PolicyViolation(pairs.differentConstraintsMessage(from, to)))
              case pairs.NewtypePair.NotSameNamedNewtypes => specialCaseYield[To]
          }

    registerSpecialCase(new SpecialCaseHandler:
      override def apply[From, To](using From: Type[From], To: Type[To]): Option[SpecialCasedTransformation[From, To]] =
        withQuotes { quotes ?=>
          val pairs: IronNewtypePairs = IronNewtypePairs(using quotes)
          pairs.pair[From, To] match
            case pairs.NewtypePair.NotSameNamedNewtypes => None
            case _                                      => Some(rewrapNewtype[From, To])
        })
