package com.histmersion
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** The SHA-256 of some bytes, as 64 lower-case hexadecimal digits. */
type Sha256 = Sha256.T
object Sha256 extends Newtype[String, Match["[0-9a-f]{64}"]]
