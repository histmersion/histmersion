package io.github.iltotore.iron.constraint.any {
  final class Pure
}

package sttp.tapir {
  class Schema[A]:
    def modify[U](path: A => U)(f: Schema[U] => Schema[U]): Schema[A] = this
    def description(text: String): Schema[A] = this

  object Schema:
    given [A]: Schema[A] = Schema[A]()
    object annotations:
      class description(text: String) extends scala.annotation.StaticAnnotation
}

package hearth.kindlings.tapirschemaderivation {
  trait KindlingsSchema[A]:
    def schema: sttp.tapir.Schema[A] = sttp.tapir.Schema[A]()

  object KindlingsSchema:
    def derived[A]: KindlingsSchema[A] = new KindlingsSchema[A] {}
}

package com.histmersion {
  trait Newtype[A, C]:
    opaque type T = A
    def apply(a: A): T = a
    extension (t: T) def value: A = t

  package util.json {
    trait JsonCodec[A]
    object JsonCodec:
      def derived[A]: JsonCodec[A] = new JsonCodec[A] {}
  }

  package api.json {
    class Schema[A]
    object Schema:
      def derived[A]: Schema[A] = Schema[A]()

    object NewtypeCodecs:
      given marker: Int = 0
  }

  package api.library {
    type Citekey = Citekey.T
    object Citekey extends Newtype[String, io.github.iltotore.iron.constraint.any.Pure]
    type Summary = Summary.T
    object Summary extends Newtype[String, io.github.iltotore.iron.constraint.any.Pure]
  }

  package api.catalogue {
    final case class Period(start: Int, end: Int)
  }

  package domain.library {
    type Citekey = Citekey.T
    object Citekey extends Newtype[String, io.github.iltotore.iron.constraint.any.Pure]
  }

  package domain {
    object EnrichSource:
      type Topic = Topic.T
      object Topic extends Newtype[String, io.github.iltotore.iron.constraint.any.Pure]
  }
}
