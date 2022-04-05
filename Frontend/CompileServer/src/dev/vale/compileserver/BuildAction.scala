package dev.vale.compileserver

import com.google.cloud.functions.{HttpFunction, HttpRequest, HttpResponse}
import dev.vale.passmanager.PassManager
import dev.vale.{Err, Interner, Ok, vimpl}
import PassManager.{Options, SourceInput, build, jsonifyProgram}

class BuildAction extends HttpFunction {
  override def service(request: HttpRequest, response: HttpResponse): Unit = {
    val code = scala.io.Source.fromInputStream(request.getInputStream).mkString
    if (code.isEmpty) {
      response.setStatusCode(400)
      response.getWriter.write("To compile a Vale program, specify the Vale code in the request body.\nExample: exported func main() int { return 42; }\n")
      return
    }

    val interner = new Interner()
    val options =
      Options(
        Vector(SourceInput(PassManager.DEFAULT_PACKAGE_COORD(interner), "in.vale", code)),
        Some(""),
        false, false, true, false, true, None, false, true, true, true)
    val json =
      PassManager.build(interner, options) match {
        case Ok(Some(programH)) => jsonifyProgram(vimpl(), programH)
        case Err(error) => {
          response.setStatusCode(400)
          response.getWriter.write(error)
          return
        }
      }

    response.getWriter.write(json + "\n")
  }
}
