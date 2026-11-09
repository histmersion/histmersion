package com.histmersion

import scala.language.strictEquality

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

object NewtypeSuite:
  type Count = Count.T
  object Count extends Newtype[Long, Pure]

  type Key = Key.T
  object Key extends Newtype[String, Not[Blank]]

  final class Opaque(val n: Int)
  type Wrapped = Wrapped.T
  object Wrapped extends Newtype[Opaque, Pure]

  type Rank = Rank.T
  object Rank extends Newtype[Int, Pure]:
    given Ordering[T] = Ordering.Int.on[T](_.value).reverse

class NewtypeSuite extends munit.FunSuite:
  import NewtypeSuite.*

  test("a list of a newtype sorts by its base type's ordering, with no import at the use site"):
    assertEquals(List(Count(3L), Count(1L), Count(2L)).sorted.map(_.value), List(1L, 2L, 3L))
    assertEquals(List(Key("b"), Key("a")).sorted.map(_.value), List("a", "b"))
    assertEquals(List(Count(3L), Count(1L)).min.value, 1L)
    assertEquals(List(Count(3L), Count(1L)).max.value, 3L)
    assertEquals(List(Count(1L), Count(3L)).sorted(using Ordering[Count].reverse).map(_.value), List(3L, 1L))

  test("the comparisons follow the newtype's ordering"):
    assert(Count(1L) < Count(2L))
    assert(Count(2L) <= Count(2L))
    assert(Count(3L) > Count(2L))
    assert(Count(2L) >= Count(2L))
    assert(!(Key("b") < Key("a")))

  test("a companion's own ordering takes precedence over the lifted one"):
    assertEquals(List(Rank(1), Rank(3), Rank(2)).sorted.map(_.value), List(3, 2, 1))
    assert(Rank(3) < Rank(1))

  test("two values of one newtype compare with == under strict equality; values of two newtypes do not compile"):
    assert(Count(1L) == Count(1L))
    assert(Count(1L) != Count(2L))
    assert(compileErrors("Count(1L) == Key(\"a\")").contains("cannot be compared with == or !="))
    assert(compileErrors("Count(1L) == 1L").contains("cannot be compared with == or !="))

  test("a base type with no ordering gives the newtype none"):
    assert(compileErrors("List(Wrapped(Opaque(1))).sorted").contains("No given instance of type Ordering"))
    assert(compileErrors("Wrapped(Opaque(1)) < Wrapped(Opaque(2))").nonEmpty)

  test("a newtype knows its full name and its constraint, as Hearth names them"):
    assertEquals(Count.typeName, Newtype.TypeName("com.histmersion.NewtypeSuite.Count"))
    assertEquals(Key.typeName, Newtype.TypeName("com.histmersion.NewtypeSuite.Key"))
    assertEquals(Count.constraint, Newtype.Constraint("True", "Always valid"))
    assertEquals(
      Key.constraint,
      Newtype.Constraint(
        "Not[DescribedAs[ForAll[Whitespace], \"Should only contain whitespaces\"]]",
        "!(Should only contain whitespaces)"
      )
    )

  test("checked gives the value, or the type, the constraint and the raw input"):
    assertEquals(Key.checked("a"), Right(Key("a")))
    assertEquals(
      Key.checked(" "),
      Left(
        Newtype.Refusal(
          Key.typeName,
          Newtype.Constraint(
            "Not[DescribedAs[ForAll[Whitespace], \"Should only contain whitespaces\"]]",
            "!(Should only contain whitespaces)"
          ),
          " "
        )
      )
    )
    assertEquals(Key.refused(" ").raw, " ")
