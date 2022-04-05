package dev.vale.typing.macros.citizen

import dev.vale.highertyping.{FunctionA, InterfaceA}
import dev.vale.postparsing.patterns.{AbstractSP, AtomSP, CaptureS}
import dev.vale.postparsing.rules.{LookupSR, RuneUsage}
import dev.vale.{Interner, RangeS}
import dev.vale.postparsing.{AbstractBodyS, CodeNameS, CodeRuneS, CodeVarNameS, CoordTemplataType, FreeDeclarationNameS, FunctionTemplataType, ParameterS, SelfNameS, TemplateTemplataType}
import dev.vale.typing.OverloadResolver
import dev.vale.typing.env.FunctionEnvEntry
import dev.vale.typing.expression.CallCompiler
import dev.vale.typing.macros.IOnInterfaceDefinedMacro
import dev.vale.typing.names.{FreeTemplateNameT, FullNameT, INameT}
import dev.vale.typing.types.{ImmutableT, MutabilityT}
import dev.vale.highertyping.FunctionA
import dev.vale.postparsing._
import dev.vale.postparsing.patterns.AbstractSP
import dev.vale.postparsing.rules.LookupSR
import dev.vale.typing.ast._
import dev.vale.typing.env.IEnvironment
import dev.vale.typing.macros.IOnInterfaceDefinedMacro
import dev.vale.typing.names._
import dev.vale.typing.types._
import dev.vale.typing.env
import dev.vale.Err

class InterfaceFreeMacro(interner: Interner, overloadCompiler: OverloadResolver) extends IOnInterfaceDefinedMacro {

  val generatorId = "interfaceFreeGenerator"

  val macroName: String = "DeriveInterfaceFree"

  override def getInterfaceSiblingEntries(interfaceName: FullNameT[INameT], interfaceA: InterfaceA): Vector[(FullNameT[INameT], FunctionEnvEntry)] = {
    Vector()
  }

  override def getInterfaceChildEntries(
    interfaceName: FullNameT[INameT],
    interfaceA: InterfaceA,
    mutability: MutabilityT):
  Vector[(FullNameT[INameT], FunctionEnvEntry)] = {
    if (mutability == ImmutableT) {
      val freeFunctionNameS = interner.intern(FreeDeclarationNameS(interfaceA.range.begin))
      val freeFunctionA =
        FunctionA(
          interfaceA.name.range,
          freeFunctionNameS,
          Vector(),
          TemplateTemplataType(Vector(CoordTemplataType), FunctionTemplataType),
          Vector(RuneUsage(RangeS.internal(interner, -64002), CodeRuneS("T"))),
          Map(CodeRuneS("T") -> CoordTemplataType, CodeRuneS("V") -> CoordTemplataType),
          Vector(
            ParameterS(
              AtomSP(
                RangeS.internal(interner, -1340),
                Some(CaptureS(interner.intern(CodeVarNameS("this")))),
                Some(AbstractSP(RangeS.internal(interner, -64002), true)),
                Some(RuneUsage(RangeS.internal(interner, -64002), CodeRuneS("T"))), None))),
          Some(RuneUsage(RangeS.internal(interner, -64002), CodeRuneS("V"))),
          Vector(
            LookupSR(RangeS.internal(interner, -1672155), RuneUsage(RangeS.internal(interner, -64002), CodeRuneS("T")), interner.intern(SelfNameS())),
            LookupSR(RangeS.internal(interner, -1672156), RuneUsage(RangeS.internal(interner, -64002), CodeRuneS("V")), interner.intern(CodeNameS("void")))),
          AbstractBodyS)

//      val virtualFreeFunctionNameS = interner.intern(AbstractVirtualFreeDeclarationNameS(interfaceA.range.begin))
//      val virtualFreeFunctionA =
//        FunctionA(
//          interfaceA.range,
//          virtualFreeFunctionNameS,
//          Vector(),
//          TemplateTemplataType(Vector(CoordTemplataType), FunctionTemplataType),
//          Vector(RuneUsage(RangeS.internal(interner, -64002), CodeRuneS("T"))),
//          Map(CodeRuneS("T") -> CoordTemplataType, CodeRuneS("V") -> CoordTemplataType),
//          Vector(
//            ParameterS(
//              AtomSP(
//                RangeS.internal(interner, -1340),
//                Some(CaptureS(interner.intern(CodeVarNameS("this")))),
//                Some(AbstractSP(RangeS.internal(interner, -1340), true)),
//                Some(RuneUsage(RangeS.internal(interner, -64002), CodeRuneS("T"))), None))),
//          Some(RuneUsage(RangeS.internal(interner, -64002), CodeRuneS("V"))),
//          Vector(
//            LookupSR(RangeS.internal(interner, -1672157), RuneUsage(RangeS.internal(interner, -64002), CodeRuneS("T")), interner.intern(SelfNameS())),
//            LookupSR(RangeS.internal(interner, -1672158), RuneUsage(RangeS.internal(interner, -64002), CodeRuneS("V")), interner.intern(CodeNameS("void")))),
//          GeneratedBodyS("abstractBody"))

      Vector(
        interfaceName.addStep(interner.intern(FreeTemplateNameT(freeFunctionNameS.codeLocationS))) ->
          FunctionEnvEntry(freeFunctionA))//,
//        interfaceName.addStep(interner.intern(AbstractVirtualFreeTemplateNameT(virtualFreeFunctionNameS.codeLoc))) ->
//          FunctionEnvEntry(virtualFreeFunctionA))
    } else {
      Vector()
    }
  }

//  override def generateFunctionBody(
//    env: FunctionEnvironment,
//    coutputs: CompilerOutputs,
//    generatorId: String,
//    life: LocationInFunctionEnvironment,
//    callRange: RangeS,
//    originFunction1: Option[FunctionA],
//    params2: Vector[ParameterT],
//    maybeRetCoord: Option[CoordT]):
//  FunctionHeaderT = {
//    val Vector(paramCoord @ CoordT(ShareT, InterfaceTT(_))) = params2.map(_.tyype)
//
//    val ret = CoordT(ShareT, VoidT())
//    val header = FunctionHeaderT(env.fullName, Vector.empty, params2, ret, originFunction1)
//
//    coutputs.declareFunctionReturnType(header.toSignature, header.returnType)
//
//    val virtualFreePrototype =
//      overloadCompiler.findFunction(
//        env, coutputs, callRange, interner.intern(VirtualFreeImpreciseNameS()), Vector(), Array(),
//        Vector(ParamFilter(paramCoord, None)), Vector(), true) match {
//        case Err(e) => throw CompileErrorExceptionT(CouldntFindFunctionToCallT(callRange, e))
//        case Ok(x) => x
//      }
//
//    val expr = FunctionCallTE(virtualFreePrototype, Vector(ArgLookupTE(0, paramCoord)))
//
//    val function2 = FunctionT(header, BlockTE(Compiler.consecutive(Vector(expr, ReturnTE(VoidLiteralTE())))))
//    coutputs.addFunction(function2)
//    function2.header}
}
