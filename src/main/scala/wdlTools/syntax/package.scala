package wdlTools.syntax

import java.net.URL

import wdlTools.syntax.AbstractSyntax.{Document, ImportDoc, Type, Expr}
import wdlTools.util.{Options, SourceCode}

import scala.collection.mutable

sealed abstract class WdlVersion(val name: String, val order: Int) extends Ordered[WdlVersion] {
  def compare(that: WdlVersion): Int = this.order - that.order
}

object WdlVersion {
  case object Draft_2 extends WdlVersion("draft-2", 0)
  case object V1 extends WdlVersion("1.0", 1)

  val All: Vector[WdlVersion] = Vector(V1, Draft_2).sortWith(_ < _)

  def fromName(name: String): WdlVersion = {
    All.collectFirst { case v if v.name == name => v }.get
  }
}

// source location in a WDL program. We add it to each syntax element
// so we could do accurate error reporting.
//
// line: line number
// col : column number
// URL:  original file or web URL
//
case class TextSource(line: Int, col: Int, url: Option[URL] = None) {
  override def toString: String = {
    if (url.isDefined) {
      s"line ${line} col ${col} of ${url}"
    } else {
      s"line ${line} col ${col}"
    }
  }
}

// Syntax error exception
final class SyntaxException(message: String) extends Exception(message) {
  def this(msg: String, text: TextSource) =
    this(s"${msg} in file ${text.url} line ${text.line} col ${text.col}")
}

/**
  * Type hierarchy for comments.
  *
  * # this is a line comment
  * # that's split across two lines
  * #
  * ## this is a preformatted comment
  */
abstract class Comment {}
case class CommentLine(text: String) extends Comment
case class CommentEmpty() extends Comment
case class CommentPreformatted(lines: Seq[String]) extends Comment
case class CommentCompound(comments: Seq[Comment]) extends Comment

trait DocumentWalker[T] {
  def walk(visitor: (URL, Document, mutable.Map[URL, T]) => Unit): Map[URL, T]
}

abstract class WdlParser(opts: Options, loader: SourceCode.Loader) {
  def canParse(sourceCode: SourceCode): Boolean

  def apply(sourceCode: SourceCode): Document

  def parse(url: URL): Document = {
    apply(loader.apply(url))
  }

  case class Walker[T](rootURL: URL,
                       sourceCode: Option[SourceCode] = None,
                       results: mutable.Map[URL, T] = mutable.HashMap.empty[URL, T])
      extends DocumentWalker[T] {
    def extractDependencies(document: Document): Map[URL, Document] = {
      document.elements.flatMap {
        case ImportDoc(_, _, url, doc, _, _) if doc.isDefined => Some(url -> doc.get)
        case _                                                => None
      }.toMap
    }

    def walk(visitor: (URL, Document, mutable.Map[URL, T]) => Unit): Map[URL, T] = {
      def addDocument(url: URL, doc: Document): Unit = {
        if (!results.contains(url)) {
          visitor(url, doc, results)
          if (opts.followImports) {
            extractDependencies(doc).foreach {
              case (uri, doc) => addDocument(uri, doc)
            }
          }
        }
      }

      val document = apply(sourceCode.getOrElse(loader.apply(rootURL)))
      addDocument(rootURL, document)
      results.toMap
    }
  }
}

trait WdlFragmentParser {
  def parseExpr(text: String): Expr
  def parseType(text: String): Type
}
