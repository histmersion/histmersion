package com.histmersion.inventoryfixture

trait SampleService:
  def read(id: Int): String

object SampleService:
  final class Implementation extends SampleService:
    def read(id: Int): String = id.toString
  final class InMemory extends SampleService:
    def read(id: Int): String = id.toString

object Unrelated:
  final class InMemory

trait Wrapper[A]
final class Wrapped extends Wrapper[SampleService]

enum Status:
  case Active, Done
  case Failed(reason: String)

trait Render[A]:
  def render(value: A): String
object Render:
  given Render[Int]:
    def render(value: Int): String = value.toString

final case class Record private (name: String)
object Record:
  def from(name: String): Either[String, Record] = Right(new Record(name))

object Retained:
  val Wire = "wire"

// External protocol fixes this spelling.
object Uses:
  def render(service: SampleService): String = service.read(1)
