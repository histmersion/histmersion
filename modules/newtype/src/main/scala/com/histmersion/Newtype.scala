package com.histmersion

import hearth.typename.TypeName as Named
import io.github.iltotore.iron.RefinedType

import Newtype.{Constraint, Refusal, TypeName}

/** The companion of every Histmersion newtype: an Iron [[io.github.iltotore.iron.RefinedType]] that also gives its type
  * the instances a value of a concept needs, once, so that no use site builds them by hand.
  *
  * {{{
  * type Citekey = Citekey.T
  * object Citekey extends Newtype[String, Not[Blank]]
  *
  * citekeys.sorted          // Ordering[Citekey], lifted from Ordering[String]
  * a == b                   // CanEqual[Citekey, Citekey], also under -language:strictEquality
  * a < b                    // the comparisons of that ordering
  * Citekey.typeName         // com.histmersion.domain.library.Citekey, as Hearth names it where it is asked
  * Citekey.checked(" ")     // Left: the type, Iron's constraint and its description, the raw input
  * }}}
  *
  * The instances are members of the companion, so they are in the implicit scope of `T`: no import is needed where the
  * type is used. A companion that defines an `Ordering[T]` of its own keeps it: a given defined in the companion takes
  * precedence over the one inherited from here.
  *
  * Every instance is an `inline given` and costs nothing at run time: a value of `T` is a value of `A`, so the ordering
  * of `T` is the ordering of `A` itself, not a wrapper around it.
  *
  * The names are Hearth's `TypeName`s, derived by the compiler: the constraint's where the companion is defined (the
  * context bound on `C`), the newtype's own where it is asked for (`typeName`, `refused`, `checked` take the
  * `TypeName[T]` of the call site, where `T` is the companion's). The constraint's description is Iron's own. No
  * companion spells any of them, so they cannot drift from the code.
  *
  * @tparam A
  *   the base type of the newtype
  * @tparam C
  *   the constraint its values satisfy
  */
trait Newtype[A, C: Named] extends RefinedType[A, C]:

  /** Two values of this newtype can be compared with `==` and `!=`; a value of another type cannot (under strict
    * equality).
    */
  inline given CanEqual[T, T] = CanEqual.derived

  /** The ordering of the base type, when it has one: values of the newtype sort as their base values do. */
  inline given (base: Ordering[A]) => Ordering[T] = assumeAll[Ordering](base)

  /** The full name of the newtype: the package and the enclosing objects of its companion, then its name. */
  def typeName(using named: Named[T]): TypeName = TypeName.of(named)

  /** The constraint every value satisfies: its type as Hearth names it (Iron's aliases followed, Iron's packages left
    * out), and Iron's description of it.
    */
  def constraint: Constraint = Constraint.of(summon[Named[C]], rtc.message)

  /** Why `raw` is no value of this newtype: this type, its constraint and the input as given. */
  def refused(raw: A)(using Named[T]): Refusal = Refusal(typeName, constraint, s"$raw")

  /** `raw` as a value of this newtype, or what it fails. */
  def checked(raw: A)(using Named[T]): Either[Refusal, T] = either(raw).left.map(_ => refused(raw))

  extension (x: T)
    /** `x` comes before `y` in the newtype's ordering. */
    inline def <(y: T)(using ordering: Ordering[T]): Boolean = ordering.lt(x, y)

    /** `x` comes before `y` in the newtype's ordering, or is equivalent to it. */
    inline def <=(y: T)(using ordering: Ordering[T]): Boolean = ordering.lteq(x, y)

    /** `x` comes after `y` in the newtype's ordering. */
    inline def >(y: T)(using ordering: Ordering[T]): Boolean = ordering.gt(x, y)

    /** `x` comes after `y` in the newtype's ordering, or is equivalent to it. */
    inline def >=(y: T)(using ordering: Ordering[T]): Boolean = ordering.gteq(x, y)

object Newtype:

  /** The full name of a type as source code writes it: `com.histmersion.domain.library.Citekey`, `a.b.Approval.Kind`.
    */
  final case class TypeName(text: String) derives CanEqual

  object TypeName:
    /** A newtype's name from Hearth's name of its `T`: `a.b.Citekey.T`, or `a.b.File$package.Citekey.T` for a companion
      * beside a top-level alias, is `a.b.Citekey`.
      */
    def of(named: Named[?]): TypeName =
      TypeName(named.plainPrint.stripSuffix(".T").split('.').filterNot(_.endsWith("$package")).mkString("."))

  /** An Iron constraint: its type as Hearth names it (`Match["[a-z]+"]`) and Iron's own description of it. */
  final case class Constraint(name: String, description: String) derives CanEqual

  object Constraint:
    /** Iron's packages, left out of a constraint's name: `Match["[a-z]+"]`, not its full path. */
    private val IronPackages = "\\bio\\.github\\.iltotore\\.iron\\.(?:constraint\\.\\w+\\.)?(?:\\w+\\$package\\.)?".r

    /** The constraint Hearth names `named`, described by Iron as `description`. */
    def of(named: Named[?], description: String): Constraint =
      Constraint(IronPackages.replaceAllIn(named.plainPrint, ""), description)

  /** A raw input that is no value of a newtype: the type, the constraint it fails and the input as given. */
  final case class Refusal(typeName: TypeName, constraint: Constraint, raw: String) derives CanEqual
