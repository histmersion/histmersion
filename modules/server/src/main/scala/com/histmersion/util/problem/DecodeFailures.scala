package com.histmersion
package util.problem

import com.histmersion.api.Api
import com.histmersion.api.json.TapirJson.*
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.problem.Problem.{Field, Invalid, Io, Raw, TypeName, Where}
import io.github.iltotore.iron.constraint.any.Pure
import sttp.capabilities.StreamMaxLengthExceededException
import sttp.model.{Header, StatusCode}
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.DecodeResult.Error.JsonDecodeException
import sttp.tapir.server.interceptor.DecodeFailureContext
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.model.ValuedEndpointOutput

object DecodeFailures:

  type Input = Input.T
  object Input extends Newtype[String, Pure]:
    def of(input: EndpointInput[?]): Input = input match
      case EndpointInput.Query(name, _, _, _)    => Input(name)
      case EndpointInput.PathCapture(name, _, _) => Input(name.getOrElse("path"))
      case EndpointInput.Cookie(name, _, _)      => Input(name)
      case EndpointIO.Header(name, _, _)         => Input(name)
      case EndpointIO.FixedHeader(header, _, _)  => Input(header.name)
      case a: EndpointInput.Auth[?, ?]           => of(a.input)
      case _: EndpointIO.Body[?, ?]              => Input("body")
      case _: EndpointIO.StreamBodyWrapper[?, ?] => Input("body")
      case _                                     => Input("request")

    def givenIn(ctx: DecodeFailureContext, input: Input): Option[Given] = ctx.failingInput match
      case _: EndpointInput.Query[?] =>
        ctx.request.queryParameters.getMulti(s"$input").flatMap(_.headOption).map(Given.of)
      case _: EndpointIO.Header[?] => ctx.request.header(s"$input").map(Given.of)
      case _                       => None

    extension (input: Input)
      def typeName: TypeName = TypeName.of(Newtype.TypeName(s"$input".capitalize))

      def problem(failure: DecodeResult.Failure): Problem = failure match
        case DecodeResult.Missing          => Invalid.Missing.here(Field.of(input))
        case DecodeResult.Multiple(values) =>
          Invalid.Repeated.here(input.typeName, Raw.of(Given.of(values.mkString(", "))))
        case DecodeResult.Mismatch(expected, actual) =>
          Invalid.Unknown.here(input.typeName, Raw.of(Given.of(actual)), Vector(Raw.of(Given.of(expected))))
        case DecodeResult.Error(_, JsonDecodeException(errors, _)) =>
          Invalid.Unparsable(
            Io.Format.Json,
            Where.of(input),
            Io.Damage.Unreadable(External.Reported.of(errors.map(_.msg).mkString(", "))),
            Vector.empty
          )
        case DecodeResult.Error(_, StreamMaxLengthExceededException(maxBytes)) =>
          Invalid.OutOfRange.here(
            input.typeName,
            Raw.of(Given.of("more")),
            Invalid.Bounds(None, Some(Raw.of(maxBytes)))
          )
        case DecodeResult.Error(original, _) =>
          Invalid.Unreadable.here(input.typeName, Raw.of(Given.of(original)), Vector.empty)
        case DecodeResult.InvalidValue(first :: _) => input.invalid(None, first)
        case DecodeResult.InvalidValue(Nil)        =>
          Invalid.Unreadable.here(input.typeName, Raw.of(Given.of("")), Vector.empty)

      def invalid(text: Option[Given], error: ValidationError[?]): Problem =
        val raw = Raw.of(text.getOrElse(Given.of(error.invalidValue)))
        error.validator match
          case e: Validator.Enumeration[?] => Invalid.Unknown.here(input.typeName, raw, known(e))
          case Validator.Min(least, _)     =>
            Invalid.OutOfRange.here(input.typeName, raw, Invalid.Bounds(Some(Raw.of(Given.of(least))), None))
          case Validator.Max(greatest, _) =>
            Invalid.OutOfRange.here(input.typeName, raw, Invalid.Bounds(None, Some(Raw.of(Given.of(greatest)))))
          case other =>
            Invalid.Malformed.here(
              input.typeName,
              Problem.Constraint.of(
                Newtype.Constraint(
                  s"$other",
                  DefaultDecodeFailureHandler.ValidationMessages.validationErrorMessage(error)
                )
              ),
              raw
            )

    private def known[T](enumeration: Validator.Enumeration[T]): Vector[Raw] =
      enumeration.possibleValues.toVector.map(v => Raw.of(Given.of(enumeration.encode.flatMap(_(v)).getOrElse(v))))

  type Given = Given.T
  object Given extends Newtype[String, Pure]:
    def of(value: Any): Given = Given(s"$value")

  val answer: EndpointOutput[(StatusCode, List[Header], Api.Error)] =
    statusCode.and(headers).and(jsonBody[Api.Error])

  val handler: DecodeFailureHandler[Identity] = DecodeFailureHandler.pure[Identity] { ctx =>
    DefaultDecodeFailureHandler.respond(ctx).map { (status, hs) =>
      ValuedEndpointOutput(answer, (status, hs, Api.Error(problems(ctx))))
    }
  }

  def problems(ctx: DecodeFailureContext): Problems =
    val input = Input.of(ctx.failingInput)
    val text = Input.givenIn(ctx, input)
    ctx.failure match
      case DecodeResult.InvalidValue(first :: more) =>
        NonEmptyVector.of(input.invalid(text, first), more.map(input.invalid(text, _))*)
      case failure => input.problem(failure)
