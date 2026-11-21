package com.histmersion

import hearth.typename.TypeName as Named
import io.github.iltotore.iron.RefinedType

import Newtype.{Constraint, Refusal, TypeName}

trait Newtype[A, C: Named] extends RefinedType[A, C]:

  inline given CanEqual[T, T] = CanEqual.derived

  inline given (base: Ordering[A]) => Ordering[T] = assumeAll[Ordering](base)

  def typeName(using named: Named[T]): TypeName = TypeName.of(named)

  def constraint: Constraint = Constraint.of(summon[Named[C]], rtc.message)

  def refused(raw: A)(using Named[T]): Refusal = Refusal(typeName, constraint, s"$raw")

  def checked(raw: A)(using Named[T]): Either[Refusal, T] = either(raw).left.map(_ => refused(raw))

  extension (x: T)
    inline def <(y: T)(using ordering: Ordering[T]): Boolean = ordering.lt(x, y)

    inline def <=(y: T)(using ordering: Ordering[T]): Boolean = ordering.lteq(x, y)

    inline def >(y: T)(using ordering: Ordering[T]): Boolean = ordering.gt(x, y)

    inline def >=(y: T)(using ordering: Ordering[T]): Boolean = ordering.gteq(x, y)

object Newtype:

  final case class TypeName(text: String)

  object TypeName:
    def of(named: Named[?]): TypeName =
      TypeName(named.plainPrint.stripSuffix(".T").split('.').filterNot(_.endsWith("$package")).mkString("."))

  final case class Constraint(name: String, description: String)

  object Constraint:
    private val IronPackages = "\\bio\\.github\\.iltotore\\.iron\\.(?:constraint\\.\\w+\\.)?(?:\\w+\\$package\\.)?".r

    def of(named: Named[?], description: String): Constraint =
      Constraint(IronPackages.replaceAllIn(named.plainPrint, ""), description)

  final case class Refusal(typeName: TypeName, constraint: Constraint, raw: String)
