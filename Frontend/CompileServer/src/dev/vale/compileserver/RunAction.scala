package dev.vale.compileserver

import java.io.{OutputStream, PrintStream}
import com.google.cloud.functions.{HttpFunction, HttpRequest, HttpResponse}
import dev.vale.passmanager.PassManager
import dev.vale.testvm.Vivem
import PassManager.{Options, SourceInput}
import dev.vale.{Err, Interner, Keywords, Ok}

class RunAction extends HttpFunction {
  override def service(request: HttpRequest, response: HttpResponse): Unit = {
    val code = scala.io.Source.fromInputStream(request.getInputStream).mkString
    if (code.isEmpty) {
      response.setStatusCode(400)
      response.getWriter.write("To compile a Vale program, specify the Vale code in the request body.\nExample: exported func main() int { return 42; }\n")
      return
    }

    val interner = new Interner()
    val keywords = new Keywords(interner)
    val options =
      Options(
        Vector(SourceInput(PassManager.DEFAULT_PACKAGE_COORD(interner, keywords), "in.vale", code)),
        Some(""),
        false, false, true, false, true, None, false, true, true, true)
    val program =
      PassManager.build(interner, keywords, options) match {
        case Ok(Some(programH)) => programH
        case Err(error) => {
          response.setStatusCode(400)
          response.getWriter.write(error)
          return
        }
      }

    Vivem.executeWithPrimitiveArgs(
      program,
      Vector(),
      new PrintStream(System.out),
      () => {
        response.setStatusCode(400)
        response.getWriter.write("Can't do stdin via HTTP!")
        throw new RuntimeException("Can't do stdin via HTTP!")
      },
      (str: String) => {
        response.getWriter.write(str)
      })
  }
}
