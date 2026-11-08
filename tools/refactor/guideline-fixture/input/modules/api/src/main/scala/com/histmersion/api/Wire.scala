package com.histmersion
package api

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema

type ApiCitekey = ApiCitekey.T
object ApiCitekey extends RefinedType[String, Pure]

final case class Finding(citekey: String, quote: String)
object Finding:
  given Schema[Finding] = Schema.derived[Finding]
    .modify(_.citekey)(_.description("The source whose record shows it")) // assert: GuidelineInventory.G48
    .modify(_.quote)( // assert: GuidelineInventory.G48
      _.description(
        "The sentence of that record, copied EXACTLY from get_source"
      )
    )
    .modify(_.quote)(_.format("q"))

final case class Box(n: Int)
object Box:
  extension [A](a: A) def modify(f: A => Any)(g: Any => Any): A = a
  val same: Box = Box(1).modify(_.n)(_.toString)
