package compilerfixture

object Positive:
  def produce(raw: String): Values.Text = raw
  def consume(text: Values.Text): String = text
  def number: Values.Count = 7
  def upper(text: Values.Text): String = text.toUpperCase(java.util.Locale.ROOT)
  def output(stream: java.io.PrintStream, text: Values.Text): Unit = stream.print(text)
  def unchanged(raw: String): String = raw.toUpperCase(java.util.Locale.ROOT)
  def combined(raw: String): Values.Text = raw.toUpperCase(java.util.Locale.ROOT) + "!"
  def optional(raw: String): Option[Values.Text] = Option(raw)
  def pattern(values: List[Values.Text]): Boolean = values match
    case "resolve" :: _ => true
    case _ => false
  def texts(values: List[String]): List[Values.Text] = values
  def maybe(value: Option[String]): Option[Values.Text] = value
  def errors(value: Either[String, Int]): Either[Values.Other, Int] = value
  def mixed(value: (String, Int)): (Values.Text, Int) = value
  def adapted(factory: () => Values.Text => Int): String => Int = factory()
  def path(text: Values.Text): java.nio.file.Path = java.nio.file.Path.of(text)
  def same(text: Values.Text): Boolean = "one" == text
  def attempted(raw: String): Option[Values.Text] = scala.util.Try(Some(raw)).toOption.flatten
  def paths(texts: List[Values.Text]): List[java.nio.file.Path] = texts.map(java.nio.file.Path.of(_))
  def failure: Either[Values.Other, Int] = scala.util.Try(throw new IllegalStateException()).toEither.left.map(e => Option(e.getMessage).getOrElse(e.getClass.getSimpleName))
