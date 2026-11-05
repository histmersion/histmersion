package com.histmersion.util.storage

import java.nio.charset.StandardCharsets.UTF_8

trait Resources:
  def text(name: com.histmersion.util.storage.Resources.TextName): Either[com.histmersion.util.storage.Resources.TextResultPart1, com.histmersion.util.storage.Resources.TextResultPart2]

object Resources:
  type ClasspathTextResultPart1 = ClasspathTextResultPart1.T
  object ClasspathTextResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClasspathTextResultPart2 = ClasspathTextResultPart2.T
  object ClasspathTextResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClasspathTextName = ClasspathTextName.T
  object ClasspathTextName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type TextResultPart1 = TextResultPart1.T
  object TextResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextResultPart2 = TextResultPart2.T
  object TextResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextName = TextName.T
  object TextName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def classpath(loader: ClassLoader): Resources = new Resources:
    def text(name: com.histmersion.util.storage.Resources.ClasspathTextName): Either[com.histmersion.util.storage.Resources.ClasspathTextResultPart1, com.histmersion.util.storage.Resources.ClasspathTextResultPart2] =
      Option(loader.getResourceAsStream(name))
        .toRight(s"missing prompt resource $name")
        .map(in =>
          try String(in.readAllBytes(), UTF_8)
          finally in.close()
        )

  final class InMemory(contents: Map[InMemory.ContentsKey, InMemory.ContentsValue]) extends Resources:
    def text(name: com.histmersion.util.storage.Resources.InMemory.TextName): Either[com.histmersion.util.storage.Resources.InMemory.TextResultPart1, com.histmersion.util.storage.Resources.InMemory.TextResultPart2] = contents.get(name).toRight(s"missing prompt resource $name")

  object InMemory:
    type ContentsKey = ContentsKey.T
    object ContentsKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ContentsValue = ContentsValue.T
    object ContentsValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type TextResultPart1 = TextResultPart1.T
    object TextResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TextResultPart2 = TextResultPart2.T
    object TextResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TextName = TextName.T
    object TextName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
