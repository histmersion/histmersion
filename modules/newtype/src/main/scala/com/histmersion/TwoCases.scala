package com.histmersion

trait TwoCases[E <: reflect.Enum]:
  def values: Array[E]

  given twoCasesOf: TwoCases[E] = this
