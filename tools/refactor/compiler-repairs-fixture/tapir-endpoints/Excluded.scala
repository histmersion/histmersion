package compilerfixture

import sttp.tapir.*

object ExcludedEndpoints:
  val custom: PublicEndpoint[WireValues.Custom, Unit, Unit, Any] = (endpoint.in(path[String]("id")): PublicEndpoint[String, Unit, Unit, Any])
  val nominal: PublicEndpoint[WireValues.Failure, Unit, Unit, Any] = (endpoint.in(path[String]("id").map(WireValues.Text(_))(_.value)): PublicEndpoint[WireValues.Text, Unit, Unit, Any])
  val widening: PublicEndpoint[Values.Large, Unit, Unit, Any] = (endpoint.in(query[Int]("n")): PublicEndpoint[Int, Unit, Unit, Any])
  val security: Endpoint[WireValues.Text, Unit, Unit, Unit, Any] = (endpoint.securityIn(header[String]("token")): Endpoint[String, Unit, Unit, Unit, Any])
  private def consume(e: PublicEndpoint[WireValues.Text, Unit, Unit, Any]): PublicEndpoint[WireValues.Text, Unit, Unit, Any] = e
  val nested: PublicEndpoint[WireValues.Text, Unit, Unit, Any] = consume((endpoint.in(path[String]("id")): PublicEndpoint[String, Unit, Unit, Any]))
  val ordinary: Values.Text = "still-unresolved"
  val customConcat: PublicEndpoint[Unit, Unit, WireValues.Custom, Any] = endpoint.out(stringBody)
  val earlierConcat: PublicEndpoint[Unit, Unit, WireValues.Text, Any] = endpoint.out(stringBody).out(statusCode(sttp.model.StatusCode.Ok))

  object Lookalike:
    final case class PublicEndpoint[A](value: A)
  val lookalike: Lookalike.PublicEndpoint[WireValues.Text] = Lookalike.PublicEndpoint("raw")
