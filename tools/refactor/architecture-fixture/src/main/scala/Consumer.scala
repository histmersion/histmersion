package com.histmersion.inventoryfixture.consumer

import com.histmersion.inventoryfixture.visibility.{PublicRepo, PublicResult, SecretToken}

object Consumer:
  def inferred = PublicRepo.read()
  def aliased(value: PublicResult): PublicResult = value
  def opaqueValue(value: SecretToken): SecretToken = value
