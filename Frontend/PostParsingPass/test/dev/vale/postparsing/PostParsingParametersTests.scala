package dev.vale.postparsing

import dev.vale.{Collector, Err, FileCoordinateMap, Interner, Ok, SourceCodeUtils, StrI, vassert, vfail, vimpl, vregionmut}
import dev.vale.options.GlobalOptions
import dev.vale.parsing.ast.BorrowP
import dev.vale.postparsing.patterns.{AtomSP, CaptureS}
import dev.vale.postparsing.rules.{AugmentSR, MaybeCoercingLookupSR, RuneUsage}
import dev.vale.parsing._
import dev.vale.parsing.ast._
import dev.vale.postparsing.patterns.AtomSP
import dev.vale.postparsing.rules._
import org.scalatest.{FunSuite, Matchers}

class PostParsingParametersTests extends FunSuite with Matchers with Collector {

  private def compile(code: String, interner: Interner = new Interner()): ProgramS = {
    val compilation = PostParserTestCompilation.test(code, interner)
    compilation.getScoutput() match {
      case Err(e) => {
        val codeMap = compilation.getCodeMap().getOrDie()
        vfail(PostParserErrorHumanizer.humanize(
          SourceCodeUtils.humanizePos(codeMap, _),
          SourceCodeUtils.linesBetween(codeMap, _, _),
          SourceCodeUtils.lineRangeContaining(codeMap, _),
          SourceCodeUtils.lineContaining(codeMap, _),
          e))
      }
      case Ok(t) => t.expectOne()
    }
  }

  private def compileForError(code: String): ICompileErrorS = {
    PostParserTestCompilation.test(code).getScoutput() match {
      case Err(e) => e
      case Ok(t) => vfail("Successfully compiled!\n" + t.toString)
    }
  }

  test("Coord rune rule") {
    val program1 = compile("""func main<T>(moo T) { }""")
    val main = program1.lookupFunction("main")

    vregionmut() // Take out with regions
    // Should have T, the default region, and the return rune
    vassert(main.runeToPredictedType.size == 2)
    // // Should have T, the default region, and the return rune
    // vassert(main.runeToPredictedType.size == 3)

    vregionmut() // see below
    main.genericParams match {
      case Vector(
        GenericParameterS(_, RuneUsage(_, CodeRuneS(StrI("T"))), CoordGenericParameterTypeS(_, _, _), None)
        // Put this back in when we have regions
        // , _ // implicit default region
        ) =>
//      case Vector(
//        GenericParameterS(
//          RangeS(_:10, _:11),RuneUsage(RangeS(_:0, _:23),CodeRuneS(StrI(T))),CoordTemplataType(),None,Vector(),None),
//        GenericParameterS(_,RuneUsage(_,DefaultRegionRuneS()),RegionTemplataType(),None,Vector(ReadWriteRuneAttributeS(_)),None))

      //        // T's implicit region rune, see MNRFGC and IRRAE.
//        GenericParameterS(_, RuneUsage(_, ImplicitRegionRuneS(CodeRuneS(StrI("T")))), RegionTemplataType(), _, _, None)) =>
    }
  }

  test("Returned rune") {
    val interner = new Interner()
    val program1 = compile("""func main<T>(moo T) T { moo }""", interner)
    val main = program1.lookupFunction("main")

    vassert(main.genericParams.map(_.rune.rune).contains(CodeRuneS(interner.intern(StrI("T")))))
    main.maybeRetCoordRune match { case Some(RuneUsage(_, CodeRuneS(StrI("T")))) => }
  }

  test("Borrowed rune") {
    val program1 = compile("""func main<T>(moo &T) { }""")
    val main = program1.lookupFunction("main")
    val Vector(param) = main.params

    val tCoordRuneFromParams =
      param match {
        case ParameterS(_,
          _,
          false,
          AtomSP(_,
            Some(CaptureS(CodeVarNameS(StrI("moo")), false)),
            Some(RuneUsage(_, tcr @ ImplicitRuneS(_))),
            None)) => tcr
      }

    val tCoordRuneFromRules =
      main.rules shouldHave {
        case AugmentSR(_, tcr, Some(BorrowP), RuneUsage(_, CodeRuneS(StrI("T")))) => tcr
      }

    tCoordRuneFromParams shouldEqual tCoordRuneFromRules.rune
  }

  test("Anonymous, typed param") {
    val program1 = compile("""func main(_ int) { }""")
    val main = program1.lookupFunction("main")
    val Vector(param) = main.params
    val paramRune =
      param match {
        case ParameterS(_,
          None,false,
          AtomSP(_,
            Some(CaptureS(CodeVarNameS(StrI(_)),false)),Some(RuneUsage(_,ImplicitRuneS(LocationInDenizen(Vector(2, 1, 1, 1, 1))))),None)) =>
        case ParameterS(_,
          _,
          false,
          AtomSP(_,
            None,
            Some(RuneUsage(_, pr @ ImplicitRuneS(_))),
            None)) => pr
      }

    main.rules shouldHave {
      case MaybeCoercingLookupSR(_, pr, CodeNameS(StrI("int"))) => vassert(pr.rune == paramRune)
    }
  }

  vregionmut() // Put back in with regions
  // test("Regioned pure function") {
  //   val bork = compile("pure func main<r', t'>(ship &r'Spaceship) t'{ }")
  //
  //   val main = bork.lookupFunction("main")
  //   main.genericParams.size shouldEqual 2
  // }

  vregionmut() // Put back in with regions
  // test("Regioned additive function") {
  //   val bork = compile("additive func main<r', t'>(ship &r'Spaceship) t'{ }")
  //
  //   val main = bork.lookupFunction("main")
  //   main.genericParams.size shouldEqual 2
  //   main.genericParams(0) match {
  //     case GenericParameterS(_,RuneUsage(_,CodeRuneS(StrI("r"))),RegionGenericParameterTypeS(ReadOnlyRegionS),None) =>
  //   }
  // }

  test("Test param-less lambda identifying runes") {
    val bork = compile(
      """
        |exported func main() int {do({ return 3; })}
        |""".stripMargin)

    val main = bork.lookupFunction("main")
    vregionmut() // Put this back in when we have regions
    // main.genericParams.size shouldEqual 1 // only the default region
    // Take this out when we have regions
    main.genericParams.size shouldEqual 0
    val lambda = Collector.onlyOf(main.body, classOf[FunctionSE])
    vregionmut() // Put this back in when we have regions
    // lambda.function.genericParams.size shouldEqual 1 // only the default region
    // Take this out when we have regions
    lambda.function.genericParams.size shouldEqual 0
  }

  test("Test one-param lambda identifying runes") {
    val bork = compile(
      """
        |exported func main() int {do({ _ })}
        |""".stripMargin)

    val main = bork.lookupFunction("main")
    vregionmut() // Put this back in when we have regions
    // main.genericParams.size shouldEqual 1 // Only the default region
    // Take this out when we have regions
    main.genericParams.size shouldEqual 0
    val lambda = Collector.onlyOf(main.body, classOf[FunctionSE])
    vregionmut() // Put this back in when we have regions
    // // magic param + default region
    // lambda.function.genericParams.size shouldEqual 2
    // Take this out when we have regions
    lambda.function.genericParams.size shouldEqual 1
  }

  test("Report that default region must be mentioned in generic params") {
    compileForError("pure func main<r'>(ship &r'Spaceship) t'{ }") match {
      case CouldntFindRuneS(range, "t") =>
    }
  }
}
