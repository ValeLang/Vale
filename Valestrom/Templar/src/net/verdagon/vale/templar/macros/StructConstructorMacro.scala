package net.verdagon.vale.templar.macros

import net.verdagon.vale.{IProfiler, PackageCoordinate, RangeS, vassert}
import net.verdagon.vale.astronomer.{ConstructorNameS, FunctionA, StructA}
import net.verdagon.vale.scout.{CodeNameS, CodeVarNameS, CoordTemplataType, FunctionTemplataType, GeneratedBodyS, IRuneS, ITemplataType, KindTemplataType, NormalStructMemberS, ParameterS, ReturnRuneS, RuneNameS, StructNameRuneS, TemplateTemplataType, UserFunctionS, VariadicStructMemberS}
import net.verdagon.vale.scout.patterns.{AtomSP, CaptureS}
import net.verdagon.vale.scout.rules.{CallSR, IRulexSR, IndexListSR, LookupSR, RuneUsage}
import net.verdagon.vale.templar.ast.{ArgLookupTE, BlockTE, ConstructTE, FunctionHeaderT, FunctionT, LocationInFunctionEnvironment, ParameterT, ReturnTE}
import net.verdagon.vale.templar.citizen.StructTemplar
import net.verdagon.vale.templar.env.{FunctionEnvEntry, FunctionEnvironment, PackageEnvironment}
import net.verdagon.vale.templar.function.{DestructorTemplar, FunctionTemplarCore}
import net.verdagon.vale.templar.names.{CitizenTemplateNameT, FullNameT, IFunctionNameT, INameT, NameTranslator, PackageTopLevelNameT}
import net.verdagon.vale.templar.{ArrayTemplar, IFunctionGenerator, TemplarOptions, Temputs}
import net.verdagon.vale.templar.types.{CoordT, InterfaceTT, MutabilityT, MutableT, OwnT, ReadonlyT, ReadwriteT, ReferenceMemberTypeT, ShareT, StructDefinitionT, StructMemberT, StructTT}

import scala.collection.mutable

class StructConstructorMacro(
  opts: TemplarOptions,
  profiler: IProfiler
) extends IOnStructDefinedMacro with IFunctionBodyMacro {

  val macroName: String = "DeriveStructConstructor"

  override def getStructChildEntries(macroName: String, structName: FullNameT[INameT], structA: StructA, mutability: MutabilityT):
  Vector[(FullNameT[INameT], FunctionEnvEntry)] = {
    Vector()
  }

  override def getStructSiblingEntries(macroName: String, structName: FullNameT[INameT], structA: StructA):
  Vector[(FullNameT[INameT], FunctionEnvEntry)] = {
    if (structA.members.collect({ case VariadicStructMemberS(_, _, _) => }).nonEmpty) {
      // Dont generate constructors for variadic structs, not supported yet.
      // Only one we have right now is tuple, which has its own special syntax for constructing.
      return Vector()
    }
    val functionA = defineConstructorFunction(structA)
    Vector(
      structName.copy(last = NameTranslator.translateNameStep(functionA.name)) ->
        FunctionEnvEntry(functionA))
  }

  private def defineConstructorFunction(structA: StructA):
  FunctionA = {
    profiler.newProfile("StructTemplarGetConstructor", structA.name.toString, () => {
      val runeToType = mutable.HashMap[IRuneS, ITemplataType]()
      runeToType ++= structA.runeToType

      val rules = mutable.ArrayBuffer[IRulexSR]()
      rules ++= structA.rules

      val retRune = RuneUsage(structA.range, ReturnRuneS())
      runeToType += (retRune.rune -> CoordTemplataType)
      if (structA.isTemplate) {
        val structNameRune = StructNameRuneS(structA.name)
        runeToType += (structNameRune -> structA.tyype)
        rules += LookupSR(structA.range, RuneUsage(structA.range, structNameRune), structA.name.getImpreciseName)
        rules += CallSR(structA.range, retRune, RuneUsage(structA.range, structNameRune), structA.identifyingRunes.toArray)
      } else {
        rules += LookupSR(structA.range, retRune, structA.name.getImpreciseName)
      }

      val params =
        structA.members.zipWithIndex.flatMap({
          case (NormalStructMemberS(range, name, variability, typeRune), index) => {
            val capture = CaptureS(CodeVarNameS(name))
            Vector(ParameterS(AtomSP(range, Some(capture), None, Some(typeRune), None)))
          }
          case (VariadicStructMemberS(range, variability, typeRune), index) => {
            Vector()
          }
        })

      val functionA =
        FunctionA(
          structA.range,
          ConstructorNameS(structA.name),
          Vector(),
          structA.tyype match {
            case KindTemplataType => FunctionTemplataType
            case TemplateTemplataType(params, KindTemplataType) => TemplateTemplataType(params, FunctionTemplataType)
          },
          structA.identifyingRunes,
          runeToType.toMap,
          params,
          Some(retRune),
          rules.toVector,
          GeneratedBodyS(generatorId))
      functionA
    })
  }

  val generatorId: String = "structConstructorGenerator"

  override def generateFunctionBody(
    env: FunctionEnvironment,
    temputs: Temputs,
    generatorId: String,
    life: LocationInFunctionEnvironment,
    callRange: RangeS,
    originFunction: Option[FunctionA],
    paramCoords: Vector[ParameterT],
    maybeRetCoord: Option[CoordT]):
  FunctionHeaderT = {
    val Some(CoordT(_, _, structTT @ StructTT(_))) = maybeRetCoord
    val structDef = temputs.lookupStruct(structTT)

    val constructorFullName = env.fullName
    vassert(constructorFullName.last.parameters.size == structDef.members.size)
    val constructorParams =
      structDef.members.map({
        case StructMemberT(name, _, ReferenceMemberTypeT(reference)) => {
          ParameterT(name, None, reference)
        }
      })
    val constructorReturnOwnership = if (structDef.mutability == MutableT) OwnT else ShareT
    val constructorReturnPermission = if (structDef.mutability == MutableT) ReadwriteT else ReadonlyT
    val constructorReturnType = CoordT(constructorReturnOwnership, constructorReturnPermission, structDef.getRef)
    // not virtual because how could a constructor be virtual
    val constructor2 =
      FunctionT(
        FunctionHeaderT(
          constructorFullName,
          Vector.empty,
          constructorParams,
          constructorReturnType,
          originFunction),
        BlockTE(
          ReturnTE(
            ConstructTE(
              structDef.getRef,
              constructorReturnType,
              constructorParams.zipWithIndex.map({ case (p, index) => ArgLookupTE(index, p.tyype) })))))

    // we cant make the destructor here because they might have a user defined one somewhere
    temputs.declareFunctionReturnType(constructor2.header.toSignature, constructor2.header.returnType)
    temputs.addFunction(constructor2);

    vassert(
      temputs.getDeclaredSignatureOrigin(
        constructor2.header.fullName).nonEmpty)

    (constructor2.header)
  }
}
