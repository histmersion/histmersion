package com.histmersion
package util.problem

import io.github.iltotore.iron.constraint.all.*

object NewtypeParseSuite:
  type ShelfRule = Match["[a-z]+"]
  type Shelf = Shelf.T
  object Shelf extends Newtype[String, ShelfRule]

  object Catalogue:
    type Year = Year.T
    object Year extends Newtype[Int, Interval.Closed[-5000, 3000]]

  type Label = Label.T
  object Label extends Newtype[String, Pure]

class NewtypeParseSuite extends munit.FunSuite:
  import NewtypeParseSuite.*
  import Problem.*

  test("a newtype knows its full name, as Hearth names its T"):
    assertEquals(Shelf.typeName.text, "com.histmersion.util.problem.NewtypeParseSuite.Shelf")
    assertEquals(Catalogue.Year.typeName.text, "com.histmersion.util.problem.NewtypeParseSuite.Catalogue.Year")

  /** Hearth's name of `Interval.Closed[-5000, 3000]`: Iron's aliases followed to what they stand for, applied. */
  private val ClosedYears =
    "DescribedAs[DescribedAs[Greater[-5000] | StrictEqual[-5000], compileTime.+[\"Should be greater than or equal to \", -5000]] & DescribedAs[Less[3000] | StrictEqual[3000], compileTime.+[\"Should be less than or equal to \", 3000]], \"Should be included in [-5000, 3000]\"]"

  test("a newtype knows its Iron constraint by Hearth's name and by Iron's description"):
    assertEquals(Shelf.constraint, Newtype.Constraint("Match[\"[a-z]+\"]", "Should match [a-z]+"))
    assertEquals(
      Catalogue.Year.constraint,
      Newtype.Constraint(ClosedYears, "Should be included in [-5000, 3000]")
    )
    assertEquals(Label.constraint, Newtype.Constraint("True", "Always valid"))

  test("parse gives the value, or Malformed with the type, the constraint and the raw input"):
    assertEquals(Shelf.parse("top"), Right(Shelf("top")))
    assertEquals(
      Shelf.parse("Top 1"),
      Left(
        Invalid.Malformed.here(
          TypeName("com.histmersion.util.problem.NewtypeParseSuite.Shelf"),
          Constraint(Constraint.Name("Match[\"[a-z]+\"]"), Constraint.Description("Should match [a-z]+")),
          Raw("Top 1")
        )
      )
    )
    assertEquals(
      Catalogue.Year.parse(9999).left.map(_.toVector),
      Left(
        Vector(
          Invalid.Malformed.here(
            TypeName("com.histmersion.util.problem.NewtypeParseSuite.Catalogue.Year"),
            Constraint(
              Constraint.Name(
                "DescribedAs[DescribedAs[Greater[-5000] | StrictEqual[-5000], compileTime.+[\"Should be greater than or equal to \", -5000]] & DescribedAs[Less[3000] | StrictEqual[3000], compileTime.+[\"Should be less than or equal to \", 3000]], \"Should be included in [-5000, 3000]\"]"
              ),
              Constraint.Description("Should be included in [-5000, 3000]")
            ),
            Raw("9999")
          )
        )
      )
    )

  test("parses accumulate: every refused input is reported"):
    val both = Shelf.parse("A").and(Catalogue.Year.parse(-9000))
    assertEquals(both.left.map(_.toVector.map(_.group)), Left(Vector(Group.Invalid, Group.Invalid)))
