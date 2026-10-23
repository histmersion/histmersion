/** The DB-backed suites run in parallel FORKED groups, each on a database of its own (build.sbt,
  * `dbTestGroups`; docs/research-build-speed.md §3). Pure: what the build computes per group. */
object TestGroups {

  /** The database a group runs on: the base URL's database name with the group spliced in before
    * its `_test` suffix, so the name still ends in `_test` (TestDb refuses anything else) —
    * `jdbc:postgresql://h:1/histmersion_test?x=y` + `server-2` → `…/histmersion_server_2_test?x=y`. */
  def groupDbUrl(base: String, group: String): String = {
    val (path, query) = base.indexOf('?') match {
      case -1 => (base, "")
      case i  => (base.substring(0, i), base.substring(i))
    }
    val slash = path.lastIndexOf('/')
    val name  = path.substring(slash + 1)
    val stem  = name.stripSuffix("_test")
    val tag   = group.toLowerCase.map(c => Some(c).filter(_.isLetterOrDigit).getOrElse('_'))
    s"${path.substring(0, slash + 1)}${stem}_${tag}_test$query"
  }

  /** `items` dealt into at most `n` non-empty groups: longest first by `weight` (unknown = 1), each to
    * the lightest group so far (ties: the lower index), names breaking ties — stable for a given input. */
  def split[A](items: Seq[A], n: Int)(name: A => String, weight: String => Double): Seq[Seq[A]] = {
    val k       = math.max(1, math.min(n, items.size))
    val buckets = Array.fill(k)(Vector.empty[A])
    val load    = Array.fill(k)(0.0)
    items.sortBy(a => (-weight(name(a)), name(a))).foreach { a =>
      val i = load.indices.minBy(j => (load(j), j))
      buckets(i) = buckets(i) :+ a
      load(i) += weight(name(a))
    }
    buckets.toSeq.filter(_.nonEmpty).map(_.sortBy(name))
  }

  /** project/test-weights.tsv (`<suite simple name> TAB <seconds>`, `#` comments) as a map; a line that
    * does not parse is skipped. */
  def weights(lines: Seq[String]): Map[String, Double] =
    lines.map(_.trim).filter(l => l.nonEmpty && !l.startsWith("#")).flatMap { l =>
      l.split("\\s+") match {
        case Array(name, secs) => scala.util.Try(secs.toDouble).toOption.filter(_ > 0).map(name -> _)
        case _                 => None
      }
    }.toMap

  /** `HISTMERSION_TEST_FORKS`-style override: a positive integer, else the default. */
  def forks(env: Option[String], default: Int): Int =
    env.flatMap(s => scala.util.Try(s.trim.toInt).toOption).filter(_ > 0).getOrElse(default)
}
