package compilerfixture

import sttp.tapir.*

object WireValues:
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Count = Count.T
  object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Flag = Flag.T
  object Flag extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Failure = Failure.T
  object Failure extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Custom = Custom.T
  object Custom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    def normalize(raw: String): T = assume(raw.trim)

object WireEndpoints:
  val tupled: PublicEndpoint[(Option[WireValues.Text], List[WireValues.Count], WireValues.Flag), String, String, Any] =
    (endpoint.get
      .in(query[Option[String]]("q"))
      .in(query[List[Int]]("n"))
      .in(query[Boolean]("flag"))
      .errorOut(stringBody)
      .out(stringBody)).mapIn[(_root_.scala.Option[_root_.compilerfixture.WireValues.Text], _root_.scala.List[_root_.compilerfixture.WireValues.Count], _root_.compilerfixture.WireValues.Flag)]((refactorDecoded: (_root_.scala.Option[_root_.java.lang.String], _root_.scala.List[_root_.scala.Int], _root_.scala.Boolean)) => (((refactorDecoded)._1).map(refactorElement => _root_.compilerfixture.WireValues.Text(refactorElement)), ((refactorDecoded)._2).map(refactorElement2 => _root_.compilerfixture.WireValues.Count(refactorElement2)), _root_.compilerfixture.WireValues.Flag((refactorDecoded)._3)))((refactorEncoded: (_root_.scala.Option[_root_.compilerfixture.WireValues.Text], _root_.scala.List[_root_.compilerfixture.WireValues.Count], _root_.compilerfixture.WireValues.Flag)) => (((refactorEncoded)._1).map(refactorElement3 => (refactorElement3).value), ((refactorEncoded)._2).map(refactorElement4 => (refactorElement4).value), ((refactorEncoded)._3).value))

  private var factories = 0
  private def rawFactory(): PublicEndpoint[String, String, String, Any] =
    factories += 1
    endpoint.in(path[String]("id")).errorOut(stringBody).out(stringBody)
  def factory(): PublicEndpoint[WireValues.Text, WireValues.Failure, WireValues.Text, Any] = (rawFactory()).mapIn[_root_.compilerfixture.WireValues.Text]((refactorDecoded2: _root_.java.lang.String) => _root_.compilerfixture.WireValues.Text(refactorDecoded2))((refactorEncoded2: _root_.compilerfixture.WireValues.Text) => (refactorEncoded2).value).mapErrorOut[_root_.compilerfixture.WireValues.Failure]((refactorDecoded3: _root_.java.lang.String) => _root_.compilerfixture.WireValues.Failure(refactorDecoded3))((refactorEncoded3: _root_.compilerfixture.WireValues.Failure) => (refactorEncoded3).value).mapOut[_root_.compilerfixture.WireValues.Text]((refactorDecoded4: _root_.java.lang.String) => _root_.compilerfixture.WireValues.Text(refactorDecoded4))((refactorEncoded4: _root_.compilerfixture.WireValues.Text) => (refactorEncoded4).value)
  val outputOnly: PublicEndpoint[Unit, Unit, WireValues.Text, Any] = (endpoint.out(stringBody)).mapOut[_root_.compilerfixture.WireValues.Text]((refactorDecoded5: _root_.java.lang.String) => _root_.compilerfixture.WireValues.Text(refactorDecoded5))((refactorEncoded5: _root_.compilerfixture.WireValues.Text) => (refactorEncoded5).value)
  val errorOnly: PublicEndpoint[Unit, WireValues.Failure, Unit, Any] = (endpoint.errorOut(stringBody)).mapErrorOut[_root_.compilerfixture.WireValues.Failure]((refactorDecoded6: _root_.java.lang.String) => _root_.compilerfixture.WireValues.Failure(refactorDecoded6))((refactorEncoded6: _root_.compilerfixture.WireValues.Failure) => (refactorEncoded6).value)
  val listOnly: PublicEndpoint[Unit, Unit, List[WireValues.Text], Any] = (endpoint.out(stringBody.map(_.split(",").toList)(_.mkString(",")))).mapOut[_root_.scala.List[_root_.compilerfixture.WireValues.Text]]((refactorDecoded7: _root_.scala.List[_root_.java.lang.String]) => (refactorDecoded7).map(refactorElement5 => _root_.compilerfixture.WireValues.Text(refactorElement5)))((refactorEncoded7: _root_.scala.List[_root_.compilerfixture.WireValues.Text]) => (refactorEncoded7).map(refactorElement6 => (refactorElement6).value))

  def check(): Unit =
    val singleOut = outputOnly.output.asInstanceOf[EndpointOutput.MappedPair[?, ?, String, WireValues.Text]].mapping
    val singleError = errorOnly.errorOutput.asInstanceOf[EndpointOutput.MappedPair[?, ?, String, WireValues.Failure]].mapping
    val listOut = listOnly.output.asInstanceOf[EndpointOutput.MappedPair[?, ?, List[String], List[WireValues.Text]]].mapping
    assert(singleOut.decode("") == DecodeResult.Value(WireValues.Text("")))
    assert(singleOut.encode(WireValues.Text("response")) == "response")
    assert(singleError.decode("failure") == DecodeResult.Value(WireValues.Failure("failure")))
    assert(singleError.encode(WireValues.Failure("failure")) == "failure")
    assert(listOut.decode(List("a", " b")) == DecodeResult.Value(List(WireValues.Text("a"), WireValues.Text(" b"))))
    assert(listOut.encode(List(WireValues.Text("a"), WireValues.Text(" b"))) == List("a", " b"))
    val mapping = tupled.input.asInstanceOf[EndpointInput.MappedPair[?, ?, (Option[String], List[Int], Boolean), (Option[WireValues.Text], List[WireValues.Count], WireValues.Flag)]].mapping
    val raw = (Some(""), List(Int.MinValue, 0, Int.MaxValue), false)
    val typed = (Some(WireValues.Text("")), List(WireValues.Count(Int.MinValue), WireValues.Count(0), WireValues.Count(Int.MaxValue)), WireValues.Flag(false))
    assert(mapping.decode(raw) == DecodeResult.Value(typed))
    assert(mapping.encode(typed) == raw)
    val empty = (None, List.empty[Int], true)
    assert(mapping.decode(empty).map(mapping.encode) == DecodeResult.Value(empty))
    assert(factories == 0)
    val built = factory()
    assert(factories == 1)
    val input = built.input.asInstanceOf[EndpointInput.MappedPair[?, ?, String, WireValues.Text]].mapping
    assert(input.decode("id") == DecodeResult.Value(WireValues.Text("id")))
    assert(input.encode(WireValues.Text("id")) == "id")
    val out = built.output.asInstanceOf[EndpointOutput.MappedPair[?, ?, String, WireValues.Text]].mapping
    val error = built.errorOutput.asInstanceOf[EndpointOutput.MappedPair[?, ?, String, WireValues.Failure]].mapping
    assert(out.decode("response") == DecodeResult.Value(WireValues.Text("response")))
    assert(out.encode(WireValues.Text("response")) == "response")
    assert(error.decode("failure") == DecodeResult.Value(WireValues.Failure("failure")))
    assert(error.encode(WireValues.Failure("failure")) == "failure")
    assert(factories == 1)
