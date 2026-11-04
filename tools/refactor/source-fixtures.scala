package com.histmersion.domain.audit

trait ExampleService:
  def bad(citekey: String): Unit // expect: primitive-role
  def typed(citekey: Citekey): Unit
  def unrelated(title: String): Unit
  private def helper(citekey: String): Unit = ()
  def callback: String => Unit = (citekey: String) => ()

object ExampleService:
  private class Implementation:
    def bad(citekey: String): Unit = ()

object PublicRepo // expect: repo-visibility
trait PublicTraitRepo // expect: repo-visibility
class PublicClassRepo // expect: repo-visibility
private[audit] object HiddenRepo
private[audit] trait HiddenTraitRepo
private[audit] class HiddenClassRepo
object RepositoryTools
private[domain] object BroadRepo // expect: repo-visibility
object Parent:
  protected class ProtectedRepo // expect: repo-visibility
  private[Parent] class NestedRepo
final case class LatLon(lat: Latitude, lon: Longitude)

trait OtherService:
  def input(citekey: String): Unit

object Values:
  final case class Input(value: String)
  def read(in: Input): String = in.value
