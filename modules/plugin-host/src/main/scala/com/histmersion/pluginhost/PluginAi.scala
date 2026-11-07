package com.histmersion.pluginhost

import com.histmersion.plugin.*

import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/** A plugin's AI provider as the host calls it: its check and each model call guarded, so an exception or a `null` from
  * plugin code is the provider's failure, never the host's.
  */
object PluginAi:
  type ModelName = ModelName.T
  object ModelName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Whether the provider can serve `model` now, by its own check; an exception is a "no". */

  def check(provider: AiProvider, model: ModelName): Either[Problem, Unit] =
    try
      Option(provider.check(AiProvider.CheckModel(model.value)))
        .getOrElse(Left(AiProvider.CheckResultPart1("its check answered null")))
        .left
        .map(e => Problem(e.value))
    catch
      case e: InterruptedException => throw e
      case NonFatal(e)             => Left(Problem(s"its check threw ${e.getClass.getSimpleName}"))

  /** One model call: the provider's answer, or its failure — an exception or a `null` being an error. */
  def turn(provider: AiProvider, request: AiTurn.Request, http: AiHttp): Either[AiFailure, AiTurn] =
    val answer =
      try Option(provider.turn(request, http)).getOrElse(Left(AiFailure.Error(com.histmersion.plugin.AiFailure.Error.Message("the provider answered null"))))
      catch
        case e: InterruptedException => throw e
        case NonFatal(e) =>
          Left(AiFailure.Error(AiFailure.Error.Message(s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}")))
    answer.flatMap(t => Option(t).toRight(AiFailure.Error(AiFailure.Error.Message("the provider answered null"))))
