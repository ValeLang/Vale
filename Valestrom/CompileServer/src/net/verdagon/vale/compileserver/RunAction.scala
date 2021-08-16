package net.verdagon.vale.compileserver

import java.io.{OutputStream, PrintStream}
import com.google.cloud.functions.{HttpFunction, HttpRequest, HttpResponse}
import net.verdagon.vale.driver.Driver
import net.verdagon.vale.driver.Driver.{Options, SourceInput}
import net.verdagon.vale.vivem.Vivem
import net.verdagon.vale.{Err, Ok}

class RunAction extends HttpFunction {
  override def service(request: HttpRequest, response: HttpResponse): Unit = {
    val code = scala.io.Source.fromInputStream(request.getInputStream).mkString
    if (code.isEmpty) {
      response.setStatusCode(400)
      response.getWriter.write("To compile a Vale program, specify the Vale code in the request body.\nExample: fn main() int export { 42 }\n")
      return
    }

    val options =
      Options(
        Vector(SourceInput(Driver.DEFAULT_PACKAGE_COORD, "in.vale", code)),
        Some(""),
        false, false, true, false, true, None, false)
    val program =
      Driver.build(options) match {
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
