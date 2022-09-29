package dev.vale.simplifying

import dev.vale.{Interner, Keywords, PackageCoordinate, vassert, vassertSome, vimpl, finalast => m}
import dev.vale.finalast.{BorrowH, EdgeH, InterfaceDefinitionH, InterfaceMethodH, InterfaceHT, KindHT, Mutable, PrototypeH, CoordH, StructDefinitionH, StructMemberH, StructHT, YonderH}
import dev.vale.typing.Hinputs
import dev.vale.typing.ast.{EdgeT, PrototypeT}
import dev.vale.typing.names._
import dev.vale.typing.templata.{CoordTemplata, MutabilityTemplata}
import dev.vale.typing.types._
import dev.vale.finalast._
import dev.vale.typing._
import dev.vale.typing.ast._
import dev.vale.typing.types._

import scala.collection.immutable.ListMap


class StructHammer(
    interner: Interner,
    keywords: Keywords,
    nameHammer: NameHammer,
    translatePrototype: (Hinputs, HamutsBox, PrototypeT) => PrototypeH,
    translateReference: (Hinputs, HamutsBox, CoordT) => CoordH[KindHT]) {
  def translateInterfaces(hinputs: Hinputs, hamuts: HamutsBox): Unit = {
    hinputs.interfaces.foreach(interface => translateInterface(hinputs, hamuts, interface.instantiatedInterface))
  }

  def translateInterfaceMethods(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      interfaceTT: InterfaceTT):
  Vector[InterfaceMethodH] = {

    val edgeBlueprint = vassertSome(hinputs.interfaceToEdgeBlueprints.get(interfaceTT.fullName))

    val methodsH =
      edgeBlueprint.superFamilyRootHeaders.map({ case (superFamilyPrototype, virtualParamIndex) =>
//        val header = vassertSome(hinputs.lookupFunction(superFamilyPrototype.toSignature)).header
        val prototypeH = translatePrototype(hinputs, hamuts, superFamilyPrototype)
        InterfaceMethodH(prototypeH, virtualParamIndex)
      })

    methodsH
  }

  def translateInterface(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    interfaceTT: InterfaceTT):
  InterfaceHT = {
    hamuts.interfaceTToInterfaceH.get(interfaceTT) match {
      case Some(structRefH) => structRefH
      case None => {
        val fullNameH = nameHammer.translateFullName(hinputs, hamuts, interfaceTT.fullName)
        // This is the only place besides InterfaceDefinitionH that can make a InterfaceRefH
        val temporaryInterfaceRefH = InterfaceHT(fullNameH);
        hamuts.forwardDeclareInterface(interfaceTT, temporaryInterfaceRefH)
        val interfaceDefT = hinputs.lookupInterface(interfaceTT.fullName);


        val methodsH = translateInterfaceMethods(hinputs, hamuts, interfaceTT)

        val interfaceDefH =
          InterfaceDefinitionH(
            fullNameH,
            interfaceDefT.weakable,
            Conversions.evaluateMutabilityTemplata(interfaceDefT.mutability),
            Vector.empty /* super interfaces */,
            methodsH)
        hamuts.addInterface(interfaceTT, interfaceDefH)
        vassert(interfaceDefH.getRef == temporaryInterfaceRefH)

        // Make sure there's a destructor for this shared interface.
        interfaceDefT.mutability match {
          case MutabilityTemplata(MutableT) => None
          case MutabilityTemplata(ImmutableT) => {
//            vassert(
//              hinputs.functions.exists(function => {
//                function.header.fullName match {
//                  case FullNameT(_, _, FreeNameT(_, _, k)) if k.kind == interfaceDefT.instantiatedInterface => true
//                  case _ => false
//                }
//              }))
          }
        }

        (interfaceDefH.getRef)
      }
    }
  }

  def translateStructs(hinputs: Hinputs, hamuts: HamutsBox): Unit = {
    hinputs.structs.foreach(structDefT => translateStructT(hinputs, hamuts, structDefT.instantiatedCitizen))
  }

  def translateStructT(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      structTT: StructTT):
  (StructHT) = {
    hamuts.structTToStructH.get(structTT) match {
      case Some(structRefH) => structRefH
      case None => {
        val (fullNameH) = nameHammer.translateFullName(hinputs, hamuts, structTT.fullName)
        // This is the only place besides StructDefinitionH that can make a StructRefH
        val temporaryStructRefH = StructHT(fullNameH);
        hamuts.forwardDeclareStruct(structTT, temporaryStructRefH)
        val structDefT = hinputs.lookupStruct(structTT.fullName);
        val (membersH) =
          translateMembers(hinputs, hamuts, structDefT.instantiatedCitizen.fullName, structDefT.members)

        val (edgesH) = translateEdgesForStruct(hinputs, hamuts, temporaryStructRefH, structTT)

        val structDefH =
          StructDefinitionH(
            fullNameH,
            structDefT.weakable,
            Conversions.evaluateMutabilityTemplata(structDefT.mutability),
            edgesH,
            membersH);
        hamuts.addStructOriginatingFromTypingPass(structTT, structDefH)
        vassert(structDefH.getRef == temporaryStructRefH)

        // Make sure there's a destructor for this shared struct.
        structDefT.mutability match {
          case MutabilityTemplata(MutableT) => None
          case MutabilityTemplata(ImmutableT) => {
//            vassert(
//              hinputs.functions.exists(function => {
//                function.header.fullName match {
//                  case FullNameT(_, _, FreeNameT(_, _, k)) if k.kind == structDefT.instantiatedCitizen => true
//                  case _ => false
//                }
//              }))
          }
        }


        (structDefH.getRef)
      }
    }
  }

  def translateMembers(hinputs: Hinputs, hamuts: HamutsBox, structName: IdT[INameT], members: Vector[IStructMemberT]):
  (Vector[StructMemberH]) = {
    members.map(translateMember(hinputs, hamuts, structName, _))
  }

  def translateMember(hinputs: Hinputs, hamuts: HamutsBox, structName: IdT[INameT], member2: IStructMemberT):
  (StructMemberH) = {
    val (variability, memberType) =
      member2 match {
        case VariadicStructMemberT(name, tyype) => vimpl()
        case NormalStructMemberT(_, variability, ReferenceMemberTypeT(coord)) => {
          (variability, translateReference(hinputs, hamuts, coord))
        }
        case NormalStructMemberT(_, variability, AddressMemberTypeT(coord)) => {
          val (referenceH) =
            translateReference(hinputs, hamuts, coord)
          val (boxStructRefH) =
            makeBox(hinputs, hamuts, variability, coord, referenceH)
          // The stack owns the box, closure structs just borrow it.
          (variability, CoordH(BorrowH, YonderH, boxStructRefH))
        }
      }
    StructMemberH(
      nameHammer.translateFullName(hinputs, hamuts, structName.addStep(member2.name)),
      Conversions.evaluateVariability(variability),
      memberType)
  }

  def makeBox(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    conceptualVariability: VariabilityT,
    type2: CoordT,
    typeH: CoordH[KindHT]):
  (StructHT) = {
    val boxFullName2 =
      IdT(
        PackageCoordinate.BUILTIN(interner, keywords),
        Vector.empty,
        interner.intern(StructNameT(
          interner.intern(StructTemplateNameT(keywords.BOX_HUMAN_NAME)),
          Vector(CoordTemplata(type2)))))
    val boxFullNameH = nameHammer.translateFullName(hinputs, hamuts, boxFullName2)
    hamuts.structDefs.find(_.fullName == boxFullNameH) match {
      case Some(structDefH) => (structDefH.getRef)
      case None => {
        val temporaryStructRefH = StructHT(boxFullNameH);

        // We don't actually care about the given variability, because even if it's final, we still need
        // the box to contain a varying reference, see VCBAAF.
        val _ = conceptualVariability
        val actualVariability = VaryingT

        val memberH =
          StructMemberH(
            nameHammer.addStep(hamuts, temporaryStructRefH.fullName, keywords.BOX_MEMBER_NAME.str),
            Conversions.evaluateVariability(actualVariability), typeH)

        val structDefH =
          StructDefinitionH(
            boxFullNameH,
            false,
            Mutable,
            Vector.empty,
            Vector(memberH));
        hamuts.addStructOriginatingFromHammer(structDefH)
        vassert(structDefH.getRef == temporaryStructRefH)
        (structDefH.getRef)
      }
    }
  }

  private def translateEdgesForStruct(
      hinputs: Hinputs, hamuts: HamutsBox,
      structRefH: StructHT,
      structTT: StructTT):
  (Vector[EdgeH]) = {
    val edges2 = hinputs.interfaceToSubCitizenToEdge.values.flatMap(_.values).filter(_.subCitizen.fullName == structTT.fullName)
    translateEdgesForStruct(hinputs, hamuts, structRefH, edges2.toVector)
  }

  private def translateEdgesForStruct(
      hinputs: Hinputs, hamuts: HamutsBox,
      structRefH: StructHT,
      edges2: Vector[EdgeT]):
  (Vector[EdgeH]) = {
    edges2.map(e => translateEdge(hinputs, hamuts, structRefH, interner.intern(InterfaceTT(e.superInterface)), e))
  }


  private def translateEdge(hinputs: Hinputs, hamuts: HamutsBox, structRefH: StructHT, interfaceTT: InterfaceTT, edge2: EdgeT):
  (EdgeH) = {
    // Purposefully not trying to translate the entire struct here, because we might hit a circular dependency
    val interfaceRefH = translateInterface(hinputs, hamuts, interfaceTT)
    val interfacePrototypesH = translateInterfaceMethods(hinputs, hamuts, interfaceTT)

    val prototypesH =
      vassertSome(hinputs.interfaceToEdgeBlueprints.get(interfaceTT.fullName))
        .superFamilyRootHeaders.map({
        case (superFamilyPrototype, virtualParamIndex) =>
          val overridePrototypeT =
            vassertSome(edge2.abstractFuncToOverrideFunc.get(superFamilyPrototype.fullName))
          val overridePrototypeH = translatePrototype(hinputs, hamuts, overridePrototypeT.overridePrototype)
          overridePrototypeH
      })

    val structPrototypesByInterfacePrototype = ListMap[InterfaceMethodH, PrototypeH](interfacePrototypesH.zip(prototypesH) : _*)
    (EdgeH(structRefH, interfaceRefH, structPrototypesByInterfacePrototype))
  }

  def lookupStruct(hinputs: Hinputs, hamuts: HamutsBox, structTT: StructTT): StructDefinitionT = {
    hinputs.lookupStruct(structTT.fullName)
  }

  def lookupInterface(hinputs: Hinputs, hamuts: HamutsBox, interfaceTT: InterfaceTT): InterfaceDefinitionT = {
    hinputs.lookupInterface(interfaceTT.fullName)
  }
}
