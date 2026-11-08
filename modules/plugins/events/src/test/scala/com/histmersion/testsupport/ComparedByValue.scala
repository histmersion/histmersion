package com.histmersion
package testsupport

import munit.Compare

/** Tests state expected values as literals; a newtype erases to its value, so comparing the two by `==` stays exact. */
object ComparedByValue:
  given comparedByValue: [A, B] => Compare[A, B] = Compare.defaultCompare[A, B]
