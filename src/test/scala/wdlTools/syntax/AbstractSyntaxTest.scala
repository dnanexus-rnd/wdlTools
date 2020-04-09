package wdlTools.syntax

import AbstractSyntax._
import java.nio.file.Paths

import org.scalatest.{FlatSpec, Matchers}
import wdlTools.syntax.v1_0.ParseAll
import wdlTools.util.{Options, SourceCode, Util}

class AbstractSyntaxTest extends FlatSpec with Matchers {
  private val tasksDir = Paths.get(getClass.getResource("/syntax/v1_0/tasks").getPath)
  private val workflowsDir = Paths.get(getClass.getResource("/syntax/v1_0/workflows").getPath)
  private val opts =
    Options(antlr4Trace = false, localDirectories = Some(Vector(tasksDir, workflowsDir)))
  private val loader = SourceCode.Loader(opts)
  private val parser = ParseAll(opts, loader)

//  private def getTaskSource(fname: String): SourceCode = {
//    loader.apply(Util.getURL(tasksDir.resolve(fname)))
//  }

  private def getWorkflowSource(fname: String): SourceCode = {
    loader.apply(Util.getURL(workflowsDir.resolve(fname)))
  }

  it should "handle import statements" in {
    val doc = parser.apply(getWorkflowSource("imports.wdl"))

    doc.version shouldBe WdlVersion.V1_0

    val imports = doc.elements.collect {
      case x: ImportDoc => x
    }
    imports.size shouldBe 2

    doc.workflow should not be empty
  }
}
