package com.histmersion
package server

import com.histmersion.lint.{movesTo, FeaturePlace}

/** The server binary's command line. It takes almost nothing — every setting is config — but what it does not know is
  * an ERROR that ends the process before anything is opened: once, `--help` started a real server against the live
  * database (docs/research-plugins.md §10.3).
  */
@movesTo(FeaturePlace.Server)
enum ServerMode:
  /** No arguments: run the server. */
  case Serve

  /** `--self-check`: load the config and the plugins, verify them, print a report and exit — no database connection, no
    * bundled runtime, no listener.
    */
  case SelfCheck

  /** `--help` / `-h`: print the usage and exit. */
  case Help
