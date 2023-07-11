package dev.vale.parsing.functions

import dev.vale.lexing.{BadFunctionBodyError, LightFunctionMustHaveParamTypes}
import dev.vale.parsing._
import dev.vale.parsing.ast._
import dev.vale.{Collector, StrI, vassertOne, vimpl}
import org.scalatest._


class AfterRegionsFunctionTests extends FunSuite with Collector with TestParseUtils {

  test("Func with func bound with missing 'where'") {
    // It parses that func moo as a templex, and apparently a return can be a templex
    compileDenizen("func sum<T>() func moo(&T)void {3}").expectErr() match {
      case null => vimpl()
    }
  }

}
