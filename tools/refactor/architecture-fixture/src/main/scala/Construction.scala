package com.histmersion.inventoryfixture

final case class Plain(value: Int)
final case class Unchecked(value: Int)
type RecordAlias = Record

object PlainFactory:
  def apply(value: Int): Plain = Plain(value)

object Construction:
  def make(value: Int): Plain = Plain(value)
  def fromFactory(value: Int): Plain = PlainFactory(value)
  def update(value: Plain): Plain = value.copy(value = 2)
  def delegated(value: String): Either[String, RecordAlias] = Record.from(value)
  def ordinary(value: String): String = value.substring(1)
  def callback(f: Int => Int): Int = f(1)
  def infix(a: Int, b: Int): Int = a + b
  def unary(a: Int): Int = -a
  def repeated(): List[Plain] = List(Plain(1), Plain(1))
