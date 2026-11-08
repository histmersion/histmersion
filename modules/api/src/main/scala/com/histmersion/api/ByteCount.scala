package com.histmersion
package api

type ByteCount = ByteCount.T
object ByteCount extends Newtype[Long, io.github.iltotore.iron.constraint.any.Pure]
