package com.histmersion.util.storage

import java.nio.charset.StandardCharsets.UTF_8

trait Resources:
  def text(name: com.histmersion.util.storage.Resources.ResourceName): Either[com.histmersion.util.storage.Resources.Problem, com.histmersion.util.storage.Blob.Text]

object Resources:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResourceName = ResourceName.T
  object ResourceName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def classpath(loader: ClassLoader): Resources = new Resources:
    def text(name: com.histmersion.util.storage.Resources.ResourceName): Either[com.histmersion.util.storage.Resources.Problem, com.histmersion.util.storage.Blob.Text] =
      Option(loader.getResourceAsStream(name.value))
        .toRight(Problem(s"missing prompt resource $name"))
        .map(in =>
          try Blob.Text(String(in.readAllBytes(), UTF_8))
          finally in.close()
        )

  final class InMemory(contents: Map[ResourceName, Blob.Text]) extends Resources:
    def text(name: com.histmersion.util.storage.Resources.ResourceName): Either[com.histmersion.util.storage.Resources.Problem, com.histmersion.util.storage.Blob.Text] = contents.get(name).toRight(Problem(s"missing prompt resource $name"))
