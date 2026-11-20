package com.histmersion
package util.database

/** The permit a test's own writes hold (a fixture's rows, a service called directly): from a gate of its own, so no
  * restore drains it. A test of the gate itself (RestoreSafetySuite, WritePermitSuite) takes its permits from the gate
  * under test instead. Import `TestPermits.given`.
  */
object TestPermits:
  given standing: WritePermit = WritePermit.standing()
