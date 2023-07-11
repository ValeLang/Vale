package dev.vale.typing

import dev.vale.Collector.ProgramWithExpect
import dev.vale.postparsing._
import dev.vale.postparsing.rules.IRulexSR
import dev.vale.solver.{FailedSolve, RuleError, Step}
import dev.vale.typing.OverloadResolver.{FindFunctionFailure, SpecificParamDoesntSend, WrongNumberOfArguments}
import dev.vale.typing.ast.{ConstantIntTE, DestroyTE, DiscardTE, FunctionCallTE, FunctionHeaderT, FunctionDefinitionT, KindExportT, LetAndLendTE, LetNormalTE, LocalLookupTE, ParameterT, PrototypeT, ReferenceMemberLookupTE, ReturnTE, SignatureT, SoftLoadTE, referenceExprResultKind, referenceExprResultStructName, _}
import dev.vale.typing.env.ReferenceLocalVariableT
import dev.vale.typing.infer.KindIsNotConcrete
import dev.vale.typing.names._
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale.{CodeLocationS, Collector, Err, FileCoordinateMap, Ok, PackageCoordinate, RangeS, Tests, vassert, vassertOne, vpass, vwat, _}
//import dev.vale.typingpass.infer.NotEnoughToSolveError
import org.scalatest._

import scala.collection.immutable.List
import scala.io.Source

class TodoTests extends FunSuite with Matchers {
}
