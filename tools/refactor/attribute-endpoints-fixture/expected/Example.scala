//> using scala 3.10.1
package fixture

import fixture.CreatorRequest
object Creator:
  enum New:
    case Unattributed, Manual

trait Named:
  def createdBy: Option[String]
trait Requested extends Named:
  def via: Option[String]

final case class Put(text: String, createdBy: Option[String] = None, via: Option[String] = None) extends Requested
final case class Tool(text: String, createdBy: Option[String] = None) extends Named
final case class Read(text: String)

object CreatorRequest:
  def rest[A](in: Requested)(body: Creator.New ?=> Either[String, A]): Either[String, A] =
    body(using in.createdBy.fold(Creator.New.Unattributed)(_ => Creator.New.Manual))
  def mcp(in: Named)(body: Creator.New ?=> String): String =
    body(using in.createdBy.fold(Creator.New.Unattributed)(_ => Creator.New.Manual))

final class Endpoint[I]:
  def serverLogic(f: I => Either[String, String]): String = "bound"
  def serverLogicSuccess(f: I => String): String = "bound"
  def handle(f: I => String): String = "bound"

object Controllers:
  def put(text: String)(using Creator.New): Either[String, String] = Right(text)
  def read(text: String): Either[String, String] = Right(text)

object ExampleRoutes:
  given Creator.New = Creator.New.Unattributed
  val put = Endpoint[(String, Put)]().serverLogic((key, in) => CreatorRequest.rest(in) { Controllers.put(key + in.text) })
  val read = Endpoint[Read]().serverLogic(in => Controllers.read(in.text))
  val bodiless = Endpoint[String]().serverLogic(key => Controllers.put(key))
  val success = Endpoint[Put]().serverLogicSuccess(in => Controllers.put(in.text).merge)
  val tool = Endpoint[Tool]().serverLogic(in => Controllers.put(in.text))
