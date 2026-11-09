package com.histmersion

import io.github.iltotore.iron.RefinedType

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
  * Citekey.typeName         // com.histmersion.domain.library.Citekey, written down by the compiler
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
  * The type's full name and the name of its constraint are written down by the compiler where the companion is defined
  * ([[Newtype.described]]), and the constraint's description is Iron's own: no companion spells any of them, so they
  * cannot drift from the code.
  *
  * @tparam A
  *   the base type of the newtype
  * @tparam C
  *   the constraint its values satisfy
  */
trait Newtype[A, C](using described: Newtype.Described[A, C]) extends RefinedType[A, C]:

  /** Two values of this newtype can be compared with `==` and `!=`; a value of another type cannot (under strict
    * equality).
    */
  inline given CanEqual[T, T] = CanEqual.derived

  /** The ordering of the base type, when it has one: values of the newtype sort as their base values do. */
  inline given (base: Ordering[A]) => Ordering[T] = assumeAll[Ordering](base)

  /** The full name of the newtype: the package and the enclosing objects of its companion, then its name. */
  def typeName: Newtype.TypeName = described.typeName

  /** The constraint every value satisfies: Iron's type as written, and Iron's description of it. */
  def constraint: Newtype.Constraint = Newtype.Constraint(described.constraintName, rtc.message)

  /** Why `raw` is no value of this newtype: this type, its constraint and the input as given. */
  def refused(raw: A): Newtype.Refusal = Newtype.Refusal(typeName, constraint, s"$raw")

  /** `raw` as a value of this newtype, or what it fails. */
  def checked(raw: A): Either[Newtype.Refusal, T] = either(raw).left.map(_ => refused(raw))

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

  /** An Iron constraint: its type as written (`Match["[a-z]+"]`, `Pure`) and Iron's own description of it. */
  final case class Constraint(name: String, description: String) derives CanEqual

  /** A raw input that is no value of a newtype: the type, the constraint it fails and the input as given. */
  final case class Refusal(typeName: TypeName, constraint: Constraint, raw: String) derives CanEqual

  /** What the compiler writes down about a newtype where its companion is defined: the companion's full name and the
    * name of the constraint. [[Newtype.described]] supplies it.
    */
  final class Described[A, C](val typeName: TypeName, val constraintName: String)

  /** The description of the newtype whose companion is being defined: the enclosing object's full name and the
    * constraint `C` as written.
    */
  inline given described: [A, C] => Described[A, C] = ${ NewtypeMacros.described[A, C] }
