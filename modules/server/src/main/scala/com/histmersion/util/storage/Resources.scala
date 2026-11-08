package com.histmersion
package util.storage

import java.nio.charset.StandardCharsets.UTF_8
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

trait Resources:
  def text(name: Resources.ResourceName): Either[Resources.Problem, Blob.Text]

object Resources:
  type Problem = Problem.T
  object Problem extends RefinedType[String, Pure]
  type ResourceName = ResourceName.T
  object ResourceName extends RefinedType[String, Pure]


  def classpath(loader: ClassLoader): Resources = new Resources:
    def text(name: ResourceName): Either[Problem, Blob.Text] =
      Option(loader.getResourceAsStream(name.value))
        .toRight(Problem(s"missing prompt resource $name"))
        .map(in =>
          try Blob.Text(String(in.readAllBytes(), UTF_8))
          finally in.close()
        )

  final class InMemory(contents: Map[ResourceName, Blob.Text]) extends Resources:
    def text(name: ResourceName): Either[Problem, Blob.Text] = contents.get(name).toRight(Problem(s"missing prompt resource $name"))
