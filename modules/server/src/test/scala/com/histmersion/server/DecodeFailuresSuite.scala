package com.histmersion
package server

import com.histmersion.api.Api
import com.histmersion.api.json.TapirJson.*
import com.histmersion.api.research.{OpenWork, ResearchApi}
import com.histmersion.util.json.RawJson
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.problem.{DecodeFailures, Problem}
import com.histmersion.util.problem.Problem.{Field, Invalid, Raw, TypeName}
import com.histmersion.util.problem.Text.sentence
import ox.supervised
import sttp.tapir.*
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.server.netty.sync.{NettySyncServer, NettySyncServerOptions}

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

class DecodeFailuresSuite extends munit.FunSuite:
  import DecodeFailures.Input

  private val who = Input("who")

  test("a value outside an enumeration is unknown, with the values the input takes"):
    val error = ValidationError(
      Validator.enumeration(OpenWork.Terms.Who.all, (w: OpenWork.Terms.Who) => Some(w.label)),
      "bogus"
    )
    val problem = who.invalid(Some(DecodeFailures.Given("bogus")), error)
    assertEquals(problem, Invalid.Unknown.here(TypeName("Who"), Raw("bogus"), Vector(Raw("owner"), Raw("agents"))))
    assertEquals(problem.sentence, "unknown who 'bogus' (one of: owner, agents)")

  test("a missing, repeated, mismatched or unreadable input is said as its own problem"):
    assertEquals(who.problem(DecodeResult.Missing), Invalid.Missing.here(Field("who")))
    assertEquals(
      who.problem(DecodeResult.Multiple(List("a", "b"))),
      Invalid.Repeated.here(TypeName("Who"), Raw("a, b"))
    )
    assertEquals(
      Input("content-type").problem(DecodeResult.Mismatch("application/json", "text/plain")),
      Invalid.Unknown.here(TypeName("Content-type"), Raw("text/plain"), Vector(Raw("application/json")))
    )
    assertEquals(
      Input("limit").problem(DecodeResult.Error("abc", NumberFormatException("abc"))),
      Invalid.Unreadable.here(TypeName("Limit"), Raw("abc"), Vector.empty)
    )
    val json =
      DecodeResult.Error("{", JsonDecodeException(List(JsonError("unexpected end of input", Nil)), Exception()))
    assertEquals(Input("body").problem(json).sentence, "the json data at body cannot be read — unexpected end of input")

  test("a bound a value is outside of is said as its range"):
    val below = Input("limit").invalid(Some(DecodeFailures.Given("0")), ValidationError(Validator.min(1), 0))
    assertEquals(below.sentence, "limit 0 is below 1")

  test("the input a failure is met in is named by the request's own name for it"):
    assertEquals(Input.of(query[String]("who")), Input("who"))
    assertEquals(Input.of(header[String]("X-Label")), Input("X-Label"))
    assertEquals(Input.of(jsonBody[Api.Error]), Input("body"))
    assertEquals(Input.of(path[String]("slug")), Input("slug"))

  private val echo = endpoint.post.in("echo").in(jsonBody[Api.Error]).out(jsonBody[Api.Error]).errorOut(Api.problemOut)
  private val counted = endpoint.get.in("counted").in(query[Int]("limit")).out(stringBody).errorOut(Api.problemOut)

  private def answer(path: String, body: Option[String]): (Int, String, String) =
    supervised {
      val options = NettySyncServerOptions.customiseInterceptors.decodeFailureHandler(DecodeFailures.handler).options
      val binding = NettySyncServer(options)
        .host("127.0.0.1")
        .port(0)
        .addEndpoint(ResearchApi.state.handle(_ => Left(Api.Error(Invalid.Missing.here(Field("never"))))))
        .addEndpoint(echo.handle(e => Right(e)))
        .addEndpoint(counted.handle(n => Right(s"$n")))
        .start()
      try
        val request = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:${binding.port}$path"))
        val sent = body.fold(request.GET())(b =>
          request.header("content-type", "application/json").POST(HttpRequest.BodyPublishers.ofString(b))
        )
        val r = HttpClient.newHttpClient().send(sent.build(), HttpResponse.BodyHandlers.ofString())
        (r.statusCode, r.headers.firstValue("content-type").orElse(""), r.body)
      finally binding.stop()
    }

  private def problems(body: String): Vector[Problem] =
    RawJson.decode[Api.Error](body).fold(e => fail(s"$e: $body"), e => NonEmptyVector.toVector(e.problems))

  test("over HTTP: an unknown filter value answers 400 with the problem body, not tapir's plain text"):
    val (status, contentType, body) = answer("/api/research/state?who=bogus", None)
    assertEquals(status, 400, body)
    assert(contentType.startsWith("application/json"), contentType)
    assertEquals(
      problems(body),
      Vector(Invalid.Unknown.here(TypeName("Who"), Raw("bogus"), Vector(Raw("owner"), Raw("agents"))))
    )

  test("over HTTP: an unreadable query value and a broken JSON body answer 400 with the problem body"):
    val (status, contentType, body) = answer("/counted?limit=many", None)
    assertEquals(status, 400, body)
    assert(contentType.startsWith("application/json"), contentType)
    assertEquals(problems(body), Vector(Invalid.Unreadable.here(TypeName("Limit"), Raw("many"), Vector.empty)))
    val (bodyStatus, _, broken) = answer("/echo", Some("""{"problems":"""))
    assertEquals(bodyStatus, 400, broken)
    assert(problems(broken).headOption.exists(_.sentence.startsWith("the json data at body cannot be read")), broken)

  test("over HTTP: a request that decodes is not touched"):
    val (status, _, body) = answer("/counted?limit=3", None)
    assertEquals((status, body), (200, "3"))
