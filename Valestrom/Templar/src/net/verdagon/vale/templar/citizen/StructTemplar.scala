package net.verdagon.vale.templar.citizen

import net.verdagon.vale.astronomer._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.parser._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.patterns.{AtomSP, CaptureS, PatternSUtils}
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.function.{DestructorTemplar, FunctionTemplar, FunctionTemplarCore, FunctionTemplarMiddleLayer}
import net.verdagon.vale._
import net.verdagon.vale.templar.OverloadTemplar.IScoutExpectedFunctionResult

import scala.collection.immutable.List

case class WeakableStructImplementingNonWeakableInterface() extends Throwable

trait IStructTemplarDelegate {
  def evaluateOrdinaryFunctionFromNonCallForHeader(
    temputs: TemputsBox,
    functionTemplata: FunctionTemplata):
  FunctionHeader2

  def scoutExpectedFunctionForPrototype(
    env: IEnvironment,
    temputs: TemputsBox,
    functionName: IImpreciseNameStepA,
    explicitlySpecifiedTemplateArgTemplexesS: List[ITemplexS],
    args: List[ParamFilter],
    extraEnvsToLookIn: List[IEnvironment],
    exact: Boolean):
  IScoutExpectedFunctionResult

  def makeImmConcreteDestructor(
    temputs: TemputsBox,
    env: IEnvironment,
    structRef2: StructRef2):
  Unit

  def getImmInterfaceDestructorOverride(
    temputs: TemputsBox,
    env: IEnvironment,
    structRef2: StructRef2,
    implementedInterfaceRefT: InterfaceRef2):
  Prototype2

  def getImmInterfaceDestructor(
    temputs: TemputsBox,
    env: IEnvironment,
    interfaceRef2: InterfaceRef2):
  Prototype2

  def getImmConcreteDestructor(
    temputs: TemputsBox,
    env: IEnvironment,
    structRef2: StructRef2):
  Prototype2
}
class StructTemplar(
    opts: TemplarOptions,
    inferTemplar: InferTemplar,
    ancestorHelper: AncestorHelper,
    delegate: IStructTemplarDelegate) {
  val templateArgsLayer = new StructTemplarTemplateArgsLayer(opts, inferTemplar, ancestorHelper, delegate)

  def addBuiltInStructs(env: NamespaceEnvironment[IName2], temputs: TemputsBox): (StructRef2) = {
    templateArgsLayer.addBuiltInStructs(env, temputs)
  }

  private def makeStructConstructor(
    temputs: TemputsBox,
    maybeConstructorOriginFunctionA: Option[FunctionA],
    structDef: StructDefinition2,
    constructorFullName: FullName2[IFunctionName2]):
  FunctionHeader2 = {
    templateArgsLayer.makeStructConstructor(temputs, maybeConstructorOriginFunctionA, structDef, constructorFullName)
  }

  def getConstructor(struct1: StructA): FunctionA = {
    opts.debugOut("todo: put all the members' rules up in the top of the struct")
    val params =
      struct1.members.zipWithIndex.map({
        case (member, index) => {
          ParameterA(
            AtomAP(
              CaptureA(CodeVarNameA(member.name), FinalP),
              None,
              MemberRuneA(index),
              None))
        }
      })
    val retRune = ReturnRuneA()
    val rules =
      struct1.rules :+
      EqualsAR(
        TemplexAR(RuneAT(retRune, CoordTemplataType)),
        TemplexAR(
          if (struct1.isTemplate) {
            CallAT(
              AbsoluteNameAT(struct1.name, struct1.tyype),
              struct1.identifyingRunes.map(rune => RuneAT(rune, struct1.typeByRune(rune))),
              CoordTemplataType)
          } else {
            AbsoluteNameAT(struct1.name, CoordTemplataType)
          }))

    val isTemplate = struct1.tyype != KindTemplataType

    FunctionA(
      ConstructorNameA(struct1.name),
      true,
      struct1.tyype match {
        case KindTemplataType => FunctionTemplataType
        case TemplateTemplataType(params, KindTemplataType) => TemplateTemplataType(params, FunctionTemplataType)
      },
      struct1.knowableRunes ++ (if (isTemplate) List() else List(retRune)),
      struct1.identifyingRunes,
      struct1.localRunes ++ List(retRune),
      struct1.typeByRune + (retRune -> CoordTemplataType),
      params,
      Some(retRune),
      rules,
      GeneratedBodyA("structConstructorGenerator"))
  }

  def getInterfaceConstructor(interfaceA: InterfaceA): FunctionA = {
    opts.debugOut("todo: put all the members' rules up in the top of the struct")
    val identifyingRunes = interfaceA.identifyingRunes
    val functorRunes = interfaceA.internalMethods.indices.map(i => (CodeRuneA("Functor" + i)))
    val typeByRune =
      interfaceA.typeByRune ++
      functorRunes.map(functorRune => (functorRune -> CoordTemplataType)).toMap +
        (AnonymousSubstructParentInterfaceRuneA() -> KindTemplataType)
    val params =
      interfaceA.internalMethods.indices.toList.map(index => {
        ParameterA(
          AtomAP(
            CaptureA(AnonymousSubstructMemberNameA(index), FinalP),
            None,
            CodeRuneA("Functor" + index),
            None))
      })
    val rules =
      interfaceA.rules :+
//        EqualsAR(
//          TemplexAR(RuneAT(retRune, CoordTemplataType)),
//          TemplexAR(
//            if (interfaceA.isTemplate) {
//              CallAT(
//                NameAT(interfaceA.name, interfaceA.tyype),
//                interfaceA.identifyingRunes.map(rune => RuneAT(rune, interfaceA.typeByRune(rune))),
//                CoordTemplataType)
//            } else {
//              NameAT(interfaceA.name, CoordTemplataType)
//            })) :+
    // We stash the interface type in the env, so that when the interface constructor generator runs,
    // it can read this to know what interface it's making a subclass of.
      EqualsAR(
        TemplexAR(RuneAT(AnonymousSubstructParentInterfaceRuneA(), KindTemplataType)),
        TemplexAR(
          if (interfaceA.isTemplate) {
            CallAT(
              AbsoluteNameAT(interfaceA.name, interfaceA.tyype),
              interfaceA.identifyingRunes.map(rune => RuneAT(rune, interfaceA.typeByRune(rune))),
              KindTemplataType)
          } else {
            AbsoluteNameAT(interfaceA.name, KindTemplataType)
          }))

    val isTemplate = interfaceA.tyype != KindTemplataType

    val TopLevelCitizenDeclarationNameA(name, codeLocation) = interfaceA.name
    FunctionA(
      FunctionNameA(name, codeLocation),
      true,
      interfaceA.tyype match {
        case KindTemplataType => FunctionTemplataType
        case TemplateTemplataType(params, KindTemplataType) => TemplateTemplataType(params, FunctionTemplataType)
      },
      interfaceA.knowableRunes ++ functorRunes ++ (if (isTemplate) List() else List(AnonymousSubstructParentInterfaceRuneA())),
      identifyingRunes,
      interfaceA.localRunes ++ functorRunes ++ List(AnonymousSubstructParentInterfaceRuneA()),
      typeByRune,
      params,
      None,
      rules,
      GeneratedBodyA("interfaceConstructorGenerator"))
  }

  def getStructRef(
    temputs: TemputsBox,
    structTemplata: StructTemplata,
    uncoercedTemplateArgs: List[ITemplata]):
  (StructRef2) = {
    templateArgsLayer.getStructRef(
      temputs, structTemplata, uncoercedTemplateArgs)
  }

  def getInterfaceRef(
    temputs: TemputsBox,
    // We take the entire templata (which includes environment and parents) so we can incorporate
    // their rules as needed
    interfaceTemplata: InterfaceTemplata,
    uncoercedTemplateArgs: List[ITemplata]):
  (InterfaceRef2) = {
    templateArgsLayer.getInterfaceRef(
      temputs, interfaceTemplata, uncoercedTemplateArgs)
  }

  // Makes a struct to back a closure
  def makeClosureUnderstruct(
    containingFunctionEnv: IEnvironment,
    temputs: TemputsBox,
    name: LambdaNameA,
    functionS: FunctionA,
    members: List[StructMember2]):
  (StructRef2, Mutability, FunctionTemplata) = {
    templateArgsLayer.makeClosureUnderstruct(containingFunctionEnv, temputs, name, functionS, members)
  }

  // Makes a struct to back a pack or tuple
  def makeSeqOrPackUnderstruct(env: NamespaceEnvironment[IName2], temputs: TemputsBox, memberTypes2: List[Coord], name: ICitizenName2):
  (StructRef2, Mutability) = {
    templateArgsLayer.makeSeqOrPackUnerstruct(env, temputs, memberTypes2, name)
  }

  // Makes an anonymous substruct of the given interface, with the given lambdas as its members.
  def makeAnonymousSubstruct(
    temputs: TemputsBox,
    interfaceRef2: InterfaceRef2,
    members: List[Coord]):
  StructRef2 = {
    val anonymousSubstructName =
      interfaceRef2.fullName.addStep(AnonymousSubstructName2(members))

    temputs.structDeclared(anonymousSubstructName) match {
      case Some(s) => return s
      case None =>
    }

    val interfaceEnv = vassertSome(temputs.envByInterfaceRef.get(interfaceRef2))
    val (s, _) =
      templateArgsLayer.makeAnonymousSubstruct(
          interfaceEnv, temputs, interfaceRef2, anonymousSubstructName)
    s
  }

  // Makes an anonymous substruct of the given interface, which just forwards its method to the given prototype.
  // This does NOT make a constructor, because its so easy to just Construct2 it.
  def prototypeToAnonymousStruct(
    temputs: TemputsBox,
    prototype: Prototype2):
  StructRef2 = {
    val structFullName = prototype.fullName.addStep(LambdaCitizenName2(CodeLocation2(-13, 0)))

    temputs.structDeclared(structFullName) match {
      case Some(structRef2) => return structRef2
      case None =>
    }

    val outerEnv = temputs.envByFunctionSignature(prototype.toSignature)
    templateArgsLayer.prototypeToAnonymousStruct(
      outerEnv, temputs, prototype, structFullName)
  }

  // This doesnt make a constructor, but its easy enough to make manually.
  def prototypeToAnonymousSubstruct(
      temputs: TemputsBox,
      interfaceRef2: InterfaceRef2,
      prototype: Prototype2):
  (StructRef2, Prototype2) = {
    val functionStructRef = prototypeToAnonymousStruct(temputs, prototype)
    val functionStructType = Coord(Share, functionStructRef)

    val lambdas = List(functionStructType)

    val anonymousSubstructRef =
      makeAnonymousSubstruct(temputs, interfaceRef2, lambdas)
    val anonymousSubstructType = Coord(Share, anonymousSubstructRef)

    val constructorName =
      prototype.fullName
        .addStep(AnonymousSubstructName2(List(functionStructType)))
        .addStep(ConstructorName2(List()))
    temputs.prototypeDeclared(constructorName) match {
      case Some(func) => return (anonymousSubstructRef, func)
      case None =>
    }

    // Now we make a function which constructs a functionStruct, then constructs a substruct.
    val constructor2 =
      Function2(
        FunctionHeader2(
          constructorName,
          false, false,
          List(),
          anonymousSubstructType,
          None),
        List(),
        Block2(
          List(
            Return2(
              Construct2(
                anonymousSubstructRef,
                anonymousSubstructType,
                List(
                  Construct2(
                    functionStructRef,
                    Coord(Share, functionStructRef),
                    List())))))))
    temputs.declareFunctionSignature(constructor2.header.toSignature, None)
    temputs.declareFunctionReturnType(constructor2.header.toSignature, constructor2.header.returnType)
    temputs.addFunction(constructor2);

    vassert(temputs.exactDeclaredSignatureExists(constructor2.header.fullName))

    (anonymousSubstructRef, constructor2.header.toPrototype)
  }

//  // Makes a functor for the given prototype.
//  def functionToLambda(
//    outerEnv: IEnvironment,
//    temputs: TemputsBox,
//    header: FunctionHeader2):
//  StructRef2 = {
//    templateArgsLayer.functionToLambda(outerEnv, temputs, header)
//  }

  def getMemberCoords(temputs: TemputsBox, structRef: StructRef2): List[Coord] = {
    temputs.structDefsByRef(structRef).members.map(_.tyype).map({
      case ReferenceMemberType2(coord) => coord
      case AddressMemberType2(_) => {
        // At time of writing, the only one who calls this is the inferer, who wants to know so it
        // can match incoming arguments into a destructure. Can we even destructure things with
        // addressible members?
        vcurious()
      }
    })
  }

  def citizenIsFromTemplate(temputs: TemputsBox, citizen: CitizenRef2, template: ITemplata): (Boolean) = {
    // this print is probably here because once we add namespaces to the syntax
    // this will false-positive for two interfaces with the same name but in different
    // namespaces.
    opts.debugOut("someday this is going to bite us")

    (citizen, template) match {
      case (InterfaceRef2(fullName), InterfaceTemplata(_, interfaceA)) => {
        fullName.last match {
          case CitizenName2(humanName, templateArgs) => humanName == interfaceA.name.name
          case _ => vimpl()
        }
      }
      case (StructRef2(fullName), StructTemplata(_, structA)) => {
        fullName.last match {
          case CitizenName2(humanName, templateArgs) => humanName == structA.name.name
          case TupleName2(_) => false
          case LambdaCitizenName2(codeLocation2) => false
          case AnonymousSubstructName2(_) => false
          case other => vimpl(other.toString)
        }
      }
      case _ => (false)
    }
  }

//  def headerToIFunctionSubclass(
//    env: IEnvironment,
//    temputs: TemputsBox,
//    header: FunctionHeader2):
//  StructRef2 = {
//    val (paramType, returnType) =
//      header.toPrototype match {
//        case Prototype2(_, List(paramType), returnType) => (paramType, returnType)
//        case _ => vimpl("Only IFunction1 implemented")
//      }
//    val Some(InterfaceTemplata(ifunction1InterfaceEnv, ifunction1InterfaceA)) =
//      env.getNearestTemplataWithName("IFunction1", Set(TemplataLookupContext))
//
//    val lambdaStructRef = functionToLambda(env, temputs, header)
//
//    val ifunction1InterfaceRef =
//      getInterfaceRef(
//        ifunction1InterfaceEnv,
//        temputs,
//        ifunction1InterfaceA,
//        List(
//          MutabilityTemplata(Immutable),
//          CoordTemplata(paramType),
//          CoordTemplata(returnType)))
//
//    makeAnonymousSubstruct()
//  }

  def prototypeToAnonymousIFunctionSubstruct(
      env: IEnvironment,
      temputs: TemputsBox,
      prototype: Prototype2):
  (InterfaceRef2, StructRef2, Prototype2) = {
    val returnType = prototype.returnType
    val List(paramType) = prototype.fullName.last.parameters

    val Some(ifunction1Templata@InterfaceTemplata(_, _)) =
      env.getNearestTemplataWithName(CodeTypeNameA("IFunction1"), Set(TemplataLookupContext))
    val ifunction1InterfaceRef =
      getInterfaceRef(
        temputs,
        ifunction1Templata,
        List(
          MutabilityTemplata(Immutable),
          CoordTemplata(paramType),
          CoordTemplata(returnType)))

    val (elementDropFunctionAsIFunctionSubstructStructRef, constructorPrototype) =
      prototypeToAnonymousSubstruct(
        temputs, ifunction1InterfaceRef, prototype)

    (ifunction1InterfaceRef, elementDropFunctionAsIFunctionSubstructStructRef, constructorPrototype)
  }
}

object StructTemplar {

  def getCompoundTypeMutability(memberTypes2: List[Coord])
  : Mutability = {
    val membersOwnerships = memberTypes2.map(_.ownership)
    val allMembersImmutable = membersOwnerships.isEmpty || membersOwnerships.toSet == Set(Share)
    if (allMembersImmutable) Immutable else Mutable
  }

  def getFunctionGenerators(): Map[String, IFunctionGenerator] = {
    Map(
      "structConstructorGenerator" ->
        new IFunctionGenerator {
          override def generate(
            functionTemplarCore: FunctionTemplarCore,
            structTemplar: StructTemplar,
            destructorTemplar: DestructorTemplar,
            env: FunctionEnvironment,
            temputs: TemputsBox,
            originFunction: Option[FunctionA],
            paramCoords: List[Parameter2],
            maybeRetCoord: Option[Coord]):
          (FunctionHeader2) = {
            val Some(Coord(_, structRef2 @ StructRef2(_))) = maybeRetCoord
            val structDef2 = temputs.lookupStruct(structRef2)
            structTemplar.makeStructConstructor(temputs, originFunction, structDef2, env.fullName)
          }
        },
      "interfaceConstructorGenerator" ->
        new IFunctionGenerator {
          override def generate(
            functionTemplarCore: FunctionTemplarCore,
            structTemplar: StructTemplar,
            destructorTemplar: DestructorTemplar,
            env: FunctionEnvironment,
            temputs: TemputsBox,
            originFunction: Option[FunctionA],
            paramCoords: List[Parameter2],
            maybeRetCoord: Option[Coord]):
          (FunctionHeader2) = {
            // The interface should be in the "__Interface" rune of the function environment.
            val interfaceRef2 =
              env.getNearestTemplataWithAbsoluteName2(AnonymousSubstructParentInterfaceRune2(), Set(TemplataLookupContext)) match {
                case Some(KindTemplata(ir @ InterfaceRef2(_))) => ir
                case _ => vwat()
              }

            val structRef2 =
              structTemplar.makeAnonymousSubstruct(
                temputs, interfaceRef2, paramCoords.map(_.tyype))
            val structDef = temputs.lookupStruct(structRef2)

            val constructorFullName = env.fullName
            val constructor =
              structTemplar.makeStructConstructor(
                temputs, originFunction, structDef, constructorFullName)

            constructor
          }
        })
  }
}