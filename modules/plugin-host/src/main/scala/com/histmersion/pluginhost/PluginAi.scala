package com.histmersion
package pluginhost

import com.histmersion.util.problem.Problem
import com.histmersion.plugin.*

import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** A plugin's AI provider as the host calls it: its check and each model call guarded, so an exception or a `null` from
  * plugin code is the provider's failure, never the host's.
  */
@atBoundary(BoundaryKind.Spi, "calls a plugin's AI provider, turning its failures into host errors")
object PluginAi:
  type ModelName = ModelName.T
  object ModelName extends Newtype[String, Pure]

  /** Whether the provider can serve `model` now, by its own check; an exception is a "no". */

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def check(provider: AiProvider, model: ModelName): Either[Problem, Unit] =
    try
      Option(provider.check(AiProvider.CheckModel(model.value)))
        .getOrElse(Left(PluginMedia.answeredNothing))
    catch
      case e: InterruptedException => throw e
      case NonFatal(e)             => Left(PluginMedia.threw(e))

  /** One model call: the provider's answer, or its failure — an exception or a `null` being an error. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def turn(provider: AiProvider, request: AiTurn.Request, http: AiHttp): Either[Problem, AiTurn] =
    val answer =
      try
        Option(provider.turn(request, http))
          .getOrElse(Left(PluginMedia.answeredNothing))
      catch
        case e: InterruptedException => throw e
        case NonFatal(e)             => Left(PluginMedia.threw(e))
    answer.flatMap(t => Option(t).toRight(PluginMedia.answeredNothing))
