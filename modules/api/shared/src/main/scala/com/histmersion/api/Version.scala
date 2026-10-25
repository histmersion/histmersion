package com.histmersion.api

/** Build identity, shared by the server and the CLI: what `/api/version` ([[VersionInfo]]) reports. */
object Version:
  val name: String    = "histmersion"
  val version: String = "0.1.0-SNAPSHOT"
  /** The SPDX id of the licence the program is distributed under (LICENSE). */
  val licence: String = "AGPL-3.0-or-later"
