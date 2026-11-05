package compilerfixture

object Positive:
  inline val version = 2
  def singleton: Values.Count = _root_.compilerfixture.Values.Count(version)
  def nullText: Values.Text = _root_.compilerfixture.Values.Text(null)
  def widened(value: Int): Values.Large = _root_.compilerfixture.Values.Large((value).toLong)
  def fallback(text: Values.Text): String = Option((text).value).getOrElse("").trim
  def unionMember(text: Values.Text): String = Option((text).value).getOrElse("").trim
  def produce(raw: String): Values.Text = _root_.compilerfixture.Values.Text(raw)
  def consume(text: Values.Text): String = (text).value
  def number: Values.Count = _root_.compilerfixture.Values.Count(7)
  def upper(text: Values.Text): String = (text).value.toUpperCase(java.util.Locale.ROOT)
  def output(stream: java.io.PrintStream, text: Values.Text): Unit = stream.print((text).value)
  def unchanged(raw: String): String = raw.toUpperCase(java.util.Locale.ROOT)
  def combined(raw: String): Values.Text = _root_.compilerfixture.Values.Text(raw.toUpperCase(java.util.Locale.ROOT) + "!")
  def optional(raw: String): Option[Values.Text] = Option(_root_.compilerfixture.Values.Text(raw))
  def pattern(values: List[Values.Text]): Boolean = values match
    case refactorPattern :: _ if (refactorPattern.value == "resolve") => true
    case _ => false
  def texts(values: List[String]): List[Values.Text] = (values).map(refactorValue => _root_.compilerfixture.Values.Text(refactorValue))
  def maybe(value: Option[String]): Option[Values.Text] = (value).map(refactorValue2 => _root_.compilerfixture.Values.Text(refactorValue2))
  def errors(value: Either[String, Int]): Either[Values.Other, Int] = (value).left.map(refactorError => _root_.compilerfixture.Values.Other(refactorError))
  def mixed(value: (String, Int)): (Values.Text, Int) = { val refactorTuple = value; (_root_.compilerfixture.Values.Text(refactorTuple._1), refactorTuple._2) }
  def adapted(factory: () => Values.Text => Int): String => Int = { val refactorFunction = factory(); (refactorValue4) => refactorFunction(_root_.compilerfixture.Values.Text(refactorValue4)) }
  def path(text: Values.Text): java.nio.file.Path = java.nio.file.Path.of((text).value)
  def same(text: Values.Text): Boolean = "one" == (text).value
  def attempted(raw: String): Option[Values.Text] = (scala.util.Try(Some(raw)).toOption.flatten[_root_.java.lang.String]).map(refactorValue5 => _root_.compilerfixture.Values.Text(refactorValue5))
  def paths(texts: List[Values.Text]): List[java.nio.file.Path] = texts.map((refactorValue6 => java.nio.file.Path.of(refactorValue6.value)))
  def failure: Either[Values.Other, Int] = scala.util.Try(throw new IllegalStateException()).toEither.left.map(e => _root_.compilerfixture.Values.Other(Option(e.getMessage).getOrElse(e.getClass.getSimpleName)))
