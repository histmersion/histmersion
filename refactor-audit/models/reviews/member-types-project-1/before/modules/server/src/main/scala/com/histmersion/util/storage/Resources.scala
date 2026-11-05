package com.histmersion.util.storage

import java.nio.charset.StandardCharsets.UTF_8

trait Resources:
  def text(name: String): Either[String, String]

object Resources:
  def classpath(loader: ClassLoader): Resources = new Resources:
    def text(name: String): Either[String, String] =
      Option(loader.getResourceAsStream(name))
        .toRight(s"missing prompt resource $name")
        .map(in =>
          try String(in.readAllBytes(), UTF_8)
          finally in.close()
        )

  final class InMemory(contents: Map[String, String]) extends Resources:
    def text(name: String): Either[String, String] = contents.get(name).toRight(s"missing prompt resource $name")
