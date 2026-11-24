package other

object Words:
  def humanize(text: String): String = text.replace('-', ' ').capitalize
