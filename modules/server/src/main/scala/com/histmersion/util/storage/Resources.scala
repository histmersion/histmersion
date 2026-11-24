package com.histmersion
package util.storage
import com.histmersion.util.problem.Problem

import java.nio.charset.StandardCharsets.UTF_8
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

trait Resources:
  def text(name: Resources.ResourceName): Either[Problem, Blob.Text]

object Resources:
  type ResourceName = ResourceName.T
  object ResourceName extends Newtype[String, Pure]

  def classpath(loader: ClassLoader): Resources = new Resources:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def text(name: ResourceName): Either[Problem, Blob.Text] =
      Option(loader.getResourceAsStream(name.value))
        .toRight(Problem.Io.Wrong(Problem.Io.Path.of(name), Problem.Io.Kind.Present))
        .map(in =>
          try Blob.Text(String(in.readAllBytes(), UTF_8))
          finally in.close()
        )

  final class InMemory(contents: Map[ResourceName, Blob.Text]) extends Resources:
    def text(name: ResourceName): Either[Problem, Blob.Text] =
      contents.get(name).toRight(Problem.Io.Wrong(Problem.Io.Path.of(name), Problem.Io.Kind.Present))
