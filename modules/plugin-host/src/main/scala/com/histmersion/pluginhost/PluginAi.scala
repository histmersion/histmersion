package com.histmersion.pluginhost

import com.histmersion.plugin.*

import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/** A plugin's AI provider as the host calls it: its check and each model call guarded, so an exception or a `null` from
  * plugin code is the provider's failure, never the host's.
  */
object PluginAi:

  /** Whether the provider can serve `model` now, by its own check; an exception is a "no". */
  type CheckResultPart1 = CheckResultPart1.T
  object CheckResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckModel = CheckModel.T
  object CheckModel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def check(provider: AiProvider, model: com.histmersion.pluginhost.PluginAi.CheckModel): Either[com.histmersion.pluginhost.PluginAi.CheckResultPart1, Unit] =
    try Option(provider.check(model)).getOrElse(Left("its check answered null"))
    catch
      case e: InterruptedException => throw e
      case NonFatal(e)             => Left(s"its check threw ${e.getClass.getSimpleName}")

  /** One model call: the provider's answer, or its failure — an exception or a `null` being an error. */
  def turn(provider: AiProvider, request: AiTurn.Request, http: AiHttp): Either[AiFailure, AiTurn] =
    val answer =
      try Option(provider.turn(request, http)).getOrElse(Left(AiFailure.Error("the provider answered null")))
      catch
        case e: InterruptedException => throw e
        case NonFatal(e) => Left(AiFailure.Error(s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}"))
    answer.flatMap(t => Option(t).toRight(AiFailure.Error("the provider answered null")))
