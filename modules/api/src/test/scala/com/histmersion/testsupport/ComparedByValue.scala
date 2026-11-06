package com.histmersion.testsupport

import munit.Compare

/** Tests state expected values as literals; a newtype erases to its value, so comparing the two by `==` stays exact. */
object ComparedByValue:
  given comparedByValue[A, B]: Compare[A, B] = new Compare[A, B] {}
