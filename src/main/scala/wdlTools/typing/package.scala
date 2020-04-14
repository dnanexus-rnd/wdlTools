package wdlTools.typing

import wdlTools.syntax.TextSource

// Type error exception
class TypeException private (ex: Exception) extends Exception(ex) {
  def this(msg: String, text: TextSource) =
    this(new Exception(s"${msg} in file ${text.url} line ${text.line} col ${text.col}"))
}

class TypeUnificationException private (ex: Exception) extends Exception(ex) {
  def this(msg : String) =
    this(new Exception(msg))
}
