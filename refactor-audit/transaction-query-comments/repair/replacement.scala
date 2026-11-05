def leadingKeyword(statement: String): String =
  def afterBlockComment(text: String): String =
    var at = 2
    var depth = 1
    while at < text.length && depth > 0 do
      if text.startsWith("/*", at) then
        depth += 1
        at += 2
      else if text.startsWith("*/", at) then
        depth -= 1
        at += 2
      else at += 1
    if depth == 0 then text.substring(at).trim else ""
  var s = statement.trim
  var go = true
  while go do
    if s.startsWith("--") then s = s.dropWhile(_ != '\n').trim
    else if s.startsWith("/*") then s = afterBlockComment(s)
    else go = false
  s.takeWhile(c => c.isLetter).toUpperCase