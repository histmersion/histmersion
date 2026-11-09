package com.histmersion
package util.problem

import scala.reflect.TypeTest

/** A vector with at least one element. At run time it is the vector itself (no wrapper), so a value that is one `A` or
  * many (`A | NonEmptyVector[A]`) is told apart by whether it is a vector: `A` must never be a vector itself.
  */
opaque type NonEmptyVector[+A] <: Matchable = Vector[A]

object NonEmptyVector:

  /** `head`, then `rest`. */
  def of[A](head: A, rest: A*): NonEmptyVector[A] = head +: rest.toVector

  /** The elements, when there is at least one. */
  def from[A](elements: Vector[A]): Option[NonEmptyVector[A]] = Option.when(elements.nonEmpty)(elements)

  /** One `A` or many, as `Left` or `Right`; `one` tells an `A` from a vector, which an `A` never is. */
  def split[A <: Matchable](value: A | NonEmptyVector[A])(using one: TypeTest[Matchable, A]): Either[A, NonEmptyVector[A]] =
    value match
      case many: Vector[? <: Matchable] => Right(many.collect { case one(a) => a })
      case one(a)                       => Left(a)

  /** Two vectors compare as their elements do. */
  given [A, B] => (elements: CanEqual[A, B]) => CanEqual[NonEmptyVector[A], NonEmptyVector[B]] = CanEqual.canEqualAny

  extension [A](elements: NonEmptyVector[A])
    def head: A = elements.head
    def tail: Vector[A] = elements.tail
    def last: A = elements.last
    def size: Int = elements.size
    def toVector: Vector[A] = elements
    def map[B](f: A => B): NonEmptyVector[B] = elements.map(f)
    def exists(p: A => Boolean): Boolean = elements.exists(p)
    def forall(p: A => Boolean): Boolean = elements.forall(p)
    def ++[B >: A](more: NonEmptyVector[B]): NonEmptyVector[B] = elements ++ more
    def concat[B >: A](more: Vector[B]): NonEmptyVector[B] = elements ++ more
    def :+[B >: A](last: B): NonEmptyVector[B] = elements :+ last
