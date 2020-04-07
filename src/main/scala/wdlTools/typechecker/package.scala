package wdlTools.typechecker

import wdlTools.syntax.TextSource

// Type error exception
class TypeException private (ex: Exception) extends Exception(ex) {
  def this(msg: String, text: TextSource) =
    this(new Exception(s"${msg} in line ${text.line} col ${text.col}"))
}
