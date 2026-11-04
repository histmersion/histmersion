//> using scala 3.10.0
package com.histmersion.cli.consolefixtures {
  import com.histmersion.util.console.Console
  object Command:
    def good(using console: Console): Unit = console.out.println("answer")
    def bad = Console.system // expect: console-factory
    def raw = println("answer") // expect: console
    def input = scala.io.StdIn.readLine() // expect: console-input
    def tty = System.console() // expect: console-input
  object Entry:
    def good = Console.system
    def raw = println("still forbidden") // expect: console
  object Lookalike:
    def println(value: String): Unit = ()
    def good = println("not a stream")
}
package com.histmersion.domain.consolefixtures {
  import com.histmersion.util.console.Console
  object Bad:
    def injected(using console: Console): Unit = () // expect: domain-console
}
