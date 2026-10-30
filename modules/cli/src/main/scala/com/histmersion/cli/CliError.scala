package com.histmersion.cli

import sttp.client4.*
import com.histmersion.api.*
import com.histmersion.config.*

/** A user-facing failure: message + process exit code (1 = usage, 2 = server said no, 3 = unreachable). */
final case class CliError(message: String, exitCode: Int = ExitCode.Unreachable)
