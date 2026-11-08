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
  * }}}
  *
  * The instances are members of the companion, so they are in the implicit scope of `T`: no import is needed where the
  * type is used. A companion that defines an `Ordering[T]` of its own keeps it: a given defined in the companion takes
  * precedence over the one inherited from here.
  *
  * Every instance is an `inline given` and costs nothing at run time: a value of `T` is a value of `A`, so the ordering
  * of `T` is the ordering of `A` itself, not a wrapper around it.
  *
  * @tparam A
  *   the base type of the newtype
  * @tparam C
  *   the constraint its values satisfy
  */
trait Newtype[A, C] extends RefinedType[A, C]:

  /** Two values of this newtype can be compared with `==` and `!=`; a value of another type cannot (under strict
    * equality).
    */
  inline given CanEqual[T, T] = CanEqual.derived

  /** The ordering of the base type, when it has one: values of the newtype sort as their base values do. */
  inline given (base: Ordering[A]) => Ordering[T] = assumeAll[Ordering](base)

  extension (x: T)
    /** `x` comes before `y` in the newtype's ordering. */
    inline def <(y: T)(using ordering: Ordering[T]): Boolean = ordering.lt(x, y)

    /** `x` comes before `y` in the newtype's ordering, or is equivalent to it. */
    inline def <=(y: T)(using ordering: Ordering[T]): Boolean = ordering.lteq(x, y)

    /** `x` comes after `y` in the newtype's ordering. */
    inline def >(y: T)(using ordering: Ordering[T]): Boolean = ordering.gt(x, y)

    /** `x` comes after `y` in the newtype's ordering, or is equivalent to it. */
    inline def >=(y: T)(using ordering: Ordering[T]): Boolean = ordering.gteq(x, y)
