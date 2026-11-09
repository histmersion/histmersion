package com.histmersion
package util.problem

class AccumulationSuite extends munit.FunSuite:
  import Problem.*
  import Problems.traverseAll

  private val a: Problem = Invalid.Missing(Field("a"))
  private val b: Problem = Invalid.Missing(Field("b"))
  private val c: Problem = NotFound.Entity(TypeName("x.Y"), Raw("c"))

  private def check(n: Int): Either[Problems, Int] = if n > 0 then Right(n) else Left(Invalid.Missing(Field(s"n$n")))

  test("one problem and many fold to their own sides and list in order"):
    val one: Problems = a
    val many: Problems = Problems.of(a, b)
    assertEquals(one.fold(_ => "one")(_ => "many"), "one")
    assertEquals(many.fold(_ => "one")(m => s"many ${m.size}"), "many 2")
    assertEquals(one.toVector, Vector(a))
    assertEquals(many.toVector, Vector(a, b))
    assertEquals(one.toNonEmpty, NonEmptyVector.of(a))

  test("++ keeps every problem in order, whatever side is one or many"):
    val one: Problems = a
    assertEquals((one ++ b).toVector, Vector(a, b))
    assertEquals((Problems.of(a, b) ++ c).toVector, Vector(a, b, c))
    assertEquals((one ++ Problems.of(b, c)).toVector, Vector(a, b, c))

  test("at puts the field in front of every input's path and leaves the rest"):
    val many: Problems = Problems.of(a, c)
    assertEquals(many.at(Field("edges")).toVector, Vector(Invalid.Missing(Field("a"), Vector(Field("edges"))), c))
    val one: Problems = a
    assertEquals(one.at(Field("edges")).toVector, Vector(Invalid.Missing(Field("a"), Vector(Field("edges")))))

  test("exists asks every problem"):
    assert(Problems.of(a, c).exists(_.isNotFound))
    assert(!(a: Problems).exists(_.isNotFound))

  test("and runs both sides and keeps the failures of both"):
    assertEquals(check(1).and(check(2)), Right((1, 2)))
    assertEquals(check(0).and(check(2)).left.map(_.toVector), Left(Vector(Invalid.Missing(Field("n0")))))
    assertEquals(check(1).and(check(-1)).left.map(_.toVector), Left(Vector(Invalid.Missing(Field("n-1")))))
    assertEquals(
      check(0).and(check(-1)).left.map(_.toVector),
      Left(Vector(Invalid.Missing(Field("n0")), Invalid.Missing(Field("n-1"))))
    )

  test("a tuple of checks gives all its values or every problem"):
    assertEquals((check(1), check(2), check(3)).allOrProblems, Right((1, 2, 3)))
    assertEquals(
      (check(0), check(2), check(-2), check(4)).allOrProblems.left.map(_.toVector),
      Left(Vector(Invalid.Missing(Field("n0")), Invalid.Missing(Field("n-2"))))
    )
    assertEquals(
      (check(1), check(2), check(3), check(4), check(5), check(6), check(7), check(8)).allOrProblems,
      Right((1, 2, 3, 4, 5, 6, 7, 8))
    )

  test("traverseAll gives all values, or every failure and not just the first"):
    assertEquals(List(1, 2, 3).traverseAll(check), Right(Vector(1, 2, 3)))
    assertEquals(List(1, 0, 3, -1).traverseAll(check).left.map(_.toVector), Left(Vector(Invalid.Missing(Field("n0")), Invalid.Missing(Field("n-1")))))
    assertEquals(List(1, 0).traverseAll(check), Left(Invalid.Missing(Field("n0"))), "one failure stays one problem")
    assertEquals(List.empty[Int].traverseAll(check), Right(Vector.empty))

  test("problems compare as values of Problems: one with one, many with many"):
    val one: Problems = a
    val same: Problems = Invalid.Missing(Field("a"))
    val many: Problems = Problems.of(a)
    assert(one == same)
    assert(one != many)
    assert(Problems.of(a, b) == Problems.of(a, b))
