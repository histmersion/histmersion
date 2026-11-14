package com.histmersion
package util.nonempty.derivation

import scala.collection.Factory
import scala.collection.mutable.Builder

import hearth.MacroCommons
import hearth.std.{ProviderResult, StandardMacroExtension, StdExtensions}

import com.histmersion.util.nonempty.NonEmptyVector

/** Tells Hearth's derivations (Kindlings' JSON codecs and Tapir schemas) that a [[NonEmptyVector]] is a collection: it
  * is written as the list of its elements, and read from a list that has at least one (an empty list is refused, never
  * read as a value). Loaded from `META-INF/services` by the macros of every module that has this one on its compile
  * class path; it runs at compile time only, so nothing of it reaches a binary.
  */
final class NonEmptyVectorProvider extends StandardMacroExtension {

  @scala.annotation.nowarn("msg=is never used")
  override def extend(ctx: MacroCommons & StdExtensions): Unit = {
    import ctx.*

    lazy val VectorType = Type.Ctor1.of[Vector]
    lazy val BuilderType = Type.Ctor2.of[Builder]

    @scala.annotation.nowarn("msg=is never used")
    def collection[A, E](tpe: Type[A], elem: Type[E]): IsCollection[A] = {
      given elemType: Type[E] = elem
      given vectorOf: Type[A] = tpe
      val vectorType: Type[Vector[E]] = VectorType[E]
      given elements: Type[Vector[E]] = vectorType
      given builder: Type[Builder[E, Vector[E]]] = BuilderType[E, Vector[E]]

      Existential[[X] =>> IsCollectionOf[A, X], E](new IsCollectionOf[A, E] {
        override def asIterable(value: Expr[A]): Expr[Iterable[E]] =
          Expr.quote(NonEmptyVector.toVector(Expr.splice(value).asInstanceOf[NonEmptyVector[E]]))
        // O(1): the elements are a Vector
        override def sizeHintForBuilder(value: Expr[A]): Option[Expr[Int]] =
          Some(Expr.quote(NonEmptyVector.size(Expr.splice(value).asInstanceOf[NonEmptyVector[E]])))
        override type CtorResult = Vector[E]
        override val CtorResult: Type[CtorResult] = vectorType
        override def factory: Expr[Factory[E, CtorResult]] = Expr.quote {
          new Factory[E, Vector[E]] {
            override def newBuilder: Builder[E, Vector[E]] = Vector.newBuilder[E]
            override def fromSpecific(it: IterableOnce[E]): Vector[E] = Vector.from(it)
          }
        }
        override def build: CtorLikeOf[Builder[E, CtorResult], A] =
          CtorLikeOf.EitherStringOrValue(
            (builder: Expr[Builder[E, Vector[E]]]) =>
              Expr.quote {
                NonEmptyVector
                  .from(Expr.splice(builder).result())
                  .map(_.asInstanceOf[A])
                  .toRight("an empty list, where at least one element belongs")
              },
            None
          )
      })
    }

    IsCollection.registerProvider(new IsCollection.Provider {
      override def name: String = "com.histmersion.util.nonempty.derivation.NonEmptyVectorProvider#NonEmptyVector"

      private lazy val Ctor = Type.Ctor1.of[NonEmptyVector]

      @scala.annotation.nowarn("msg=is never used")
      override def parse[A](tpe: Type[A]): ProviderResult[IsCollection[A]] =
        Ctor.unapply(tpe) match {
          case Some(elem) => ProviderResult.Matched(collection(tpe, elem.Underlying))
          case None       => skippedLazily(s"${tpe.prettyPrint} is not a NonEmptyVector")
        }
    })
  }
}
