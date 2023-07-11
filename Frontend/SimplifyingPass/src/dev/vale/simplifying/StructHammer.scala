package dev.vale.simplifying

import dev.vale.{Interner, Keywords, PackageCoordinate, vassert, vassertSome, vimpl, vregionmut, finalast => m}
import dev.vale.finalast._
import dev.vale.instantiating.ast._

import scala.collection.immutable.ListMap


class StructHammer(
    interner: Interner,
    keywords: Keywords,
    nameHammer: NameHammer,
    translatePrototype: (HinputsI, HamutsBox, PrototypeI[cI]) => PrototypeH,
    translateReference: (HinputsI, HamutsBox, CoordI[cI]) => CoordH[KindHT]) {
  def translateInterfaces(hinputs: HinputsI, hamuts: HamutsBox): Unit = {
    hinputs.interfaces.foreach(interface => translateInterface(hinputs, hamuts, interface.instantiatedInterface))
  }

  def translateInterfaceMethods(
      hinputs: HinputsI,
      hamuts: HamutsBox,
      interfaceTT: InterfaceIT[cI]):
  Vector[InterfaceMethodH] = {

    val edgeBlueprint = vassertSome(hinputs.interfaceToEdgeBlueprints.get(interfaceTT.id))

    val methodsH =
      edgeBlueprint.superFamilyRootHeaders.map({ case (superFamilyPrototype, virtualParamIndex) =>
//        val header = vassertSome(hinputs.lookupFunction(superFamilyPrototype.toSignature)).header
        val prototypeH = translatePrototype(hinputs, hamuts, superFamilyPrototype)
        InterfaceMethodH(prototypeH, virtualParamIndex)
      })

    methodsH
  }

  def translateInterface(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    interfaceIT: InterfaceIT[cI]):
  InterfaceHT = {
    hamuts.interfaceTToInterfaceH.get(interfaceIT) match {
      case Some(structRefH) => structRefH
      case None => {
        val fullNameH = nameHammer.translateFullName(hinputs, hamuts, interfaceIT.id)
        // This is the only place besides InterfaceDefinitionH that can make a InterfaceRefH
        val temporaryInterfaceRefH = InterfaceHT(fullNameH);
        hamuts.forwardDeclareInterface(interfaceIT, temporaryInterfaceRefH)
        val interfaceDefI = hinputs.lookupInterface(interfaceIT.id);


        val methodsH = translateInterfaceMethods(hinputs, hamuts, interfaceIT)

        val interfaceDefH =
          InterfaceDefinitionH(
            fullNameH,
            interfaceDefI.weakable,
            Conversions.evaluateMutabilityTemplata(interfaceDefI.mutability),
            Vector.empty /* super interfaces */,
            methodsH)
        hamuts.addInterface(interfaceIT, interfaceDefH)
        vassert(interfaceDefH.getRef == temporaryInterfaceRefH)

        // Make sure there's a destructor for this shared interface.
        interfaceDefI.mutability match {
          case MutableI => None
          case ImmutableI => {
//            vassert(
//              hinputs.functions.exists(function => {
//                function.header.fullName match {
//                  case FullNameI(_, _, FreeNameI(_, _, k)) if k.kind == interfaceDefI.instantiatedInterface => true
//                  case _ => false
//                }
//              }))
          }
        }

        (interfaceDefH.getRef)
      }
    }
  }

  def translateStructs(hinputs: HinputsI, hamuts: HamutsBox): Unit = {
    hinputs.structs.foreach(structDefI => translateStructI(hinputs, hamuts, structDefI.instantiatedCitizen))
  }

  def translateStructI(
      hinputs: HinputsI,
      hamuts: HamutsBox,
      structIT: StructIT[cI]):
  (StructHT) = {
    hamuts.structTToStructH.get(structIT) match {
      case Some(structRefH) => structRefH
      case None => {
        val (fullNameH) = nameHammer.translateFullName(hinputs, hamuts, structIT.id)
        // This is the only place besides StructDefinitionH that can make a StructRefH
        val temporaryStructRefH = StructHT(fullNameH);
        hamuts.forwardDeclareStruct(structIT, temporaryStructRefH)
        val structDefI = hinputs.lookupStruct(structIT.id);
        val (membersH) =
          translateMembers(hinputs, hamuts, structDefI.instantiatedCitizen.id, structDefI.members)

        val (edgesH) = translateEdgesForStruct(hinputs, hamuts, temporaryStructRefH, structIT)

        val structDefH =
          StructDefinitionH(
            fullNameH,
            structDefI.weakable,
            Conversions.evaluateMutabilityTemplata(structDefI.mutability),
            edgesH,
            membersH);
        hamuts.addStructOriginatingFromTypingPass(structIT, structDefH)
        vassert(structDefH.getRef == temporaryStructRefH)

        // Make sure there's a destructor for this shared struct.
        structDefI.mutability match {
          case MutableI => None
          case ImmutableI => {
//            vassert(
//              hinputs.functions.exists(function => {
//                function.header.fullName match {
//                  case FullNameI(_, _, FreeNameI(_, _, k)) if k.kind == structDefI.instantiatedCitizen => true
//                  case _ => false
//                }
//              }))
          }
        }


        (structDefH.getRef)
      }
    }
  }

  def translateMembers(hinputs: HinputsI, hamuts: HamutsBox, structName: IdI[cI, INameI[cI]], members: Vector[StructMemberI]):
  (Vector[StructMemberH]) = {
    members.map(translateMember(hinputs, hamuts, structName, _))
  }

  def translateMember(hinputs: HinputsI, hamuts: HamutsBox, structName: IdI[cI, INameI[cI]], member2: StructMemberI):
  (StructMemberH) = {
    val (variability, memberType) =
      member2 match {
//        case VariadicStructMemberI(name, tyype) => vimpl()
        case StructMemberI(_, variability, ReferenceMemberTypeI(coord)) => {
          (variability, translateReference(hinputs, hamuts, coord))
        }
        case StructMemberI(_, variability, AddressMemberTypeI(coord)) => {
          val (referenceH) =
            translateReference(hinputs, hamuts, coord)
          val (boxStructRefH) =
            makeBox(hinputs, hamuts, variability, coord, referenceH)
          // The stack owns the box, closure structs just borrow it.
          (variability, CoordH(vregionmut(MutableBorrowH), YonderH, boxStructRefH))
        }
      }
    StructMemberH(
      nameHammer.translateFullName(hinputs, hamuts, INameI.addStep(structName, member2.name)),
      Conversions.evaluateVariability(variability),
      memberType)
  }

  def makeBox(
    hinputs: HinputsI,
    hamuts: HamutsBox,
    conceptualVariability: VariabilityI,
    type2: CoordI[cI],
    typeH: CoordH[KindHT]):
  (StructHT) = {
    val boxFullName2 =
      IdI(
        PackageCoordinate.BUILTIN(interner, keywords),
        Vector[INameI[cI]](),
        StructNameI[cI](
          StructTemplateNameI[cI](keywords.BOX_HUMAN_NAME),
          Vector(CoordTemplataI[cI](RegionTemplataI(0), type2))))
    val boxFullNameH = nameHammer.translateFullName(hinputs, hamuts, boxFullName2)
    hamuts.structDefs.find(_.id == boxFullNameH) match {
      case Some(structDefH) => (structDefH.getRef)
      case None => {
        val temporaryStructRefH = StructHT(boxFullNameH);

        // We don't actually care about the given variability, because even if it's final, we still need
        // the box to contain a varying reference, see VCBAAF.
        val _ = conceptualVariability
        val actualVariability = VaryingI

        val memberH =
          StructMemberH(
            nameHammer.addStep(hamuts, temporaryStructRefH.id, keywords.BOX_MEMBER_NAME.str),
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
      hinputs: HinputsI, hamuts: HamutsBox,
      structRefH: StructHT,
      structTT: StructIT[cI]):
  (Vector[EdgeH]) = {
    val edges2 = hinputs.interfaceToSubCitizenToEdge.values.flatMap(_.values).filter(_.subCitizen.id == structTT.id)
    translateEdgesForStruct(hinputs, hamuts, structRefH, edges2.toVector)
  }

  private def translateEdgesForStruct(
      hinputs: HinputsI, hamuts: HamutsBox,
      structRefH: StructHT,
      edges2: Vector[EdgeI]):
  (Vector[EdgeH]) = {
    edges2.map(e => translateEdge(hinputs, hamuts, structRefH, InterfaceIT(e.superInterface), e))
  }


  private def translateEdge(hinputs: HinputsI, hamuts: HamutsBox, structRefH: StructHT, interfaceIT: InterfaceIT[cI], edge2: EdgeI):
  (EdgeH) = {
    // Purposefully not trying to translate the entire struct here, because we might hit a circular dependency
    val interfaceRefH = translateInterface(hinputs, hamuts, interfaceIT)
    val interfacePrototypesH = translateInterfaceMethods(hinputs, hamuts, interfaceIT)

    val prototypesH =
      vassertSome(hinputs.interfaceToEdgeBlueprints.get(interfaceIT.id))
        .superFamilyRootHeaders.map({
        case (superFamilyPrototype, virtualParamIndex) =>
          val overridePrototypeI =
            vassertSome(edge2.abstractFuncToOverrideFunc.get(superFamilyPrototype.id))
          val overridePrototypeH = translatePrototype(hinputs, hamuts, overridePrototypeI)
          overridePrototypeH
      })

    val structPrototypesByInterfacePrototype = ListMap[InterfaceMethodH, PrototypeH](interfacePrototypesH.zip(prototypesH) : _*)
    (EdgeH(structRefH, interfaceRefH, structPrototypesByInterfacePrototype))
  }

  def lookupStruct(hinputs: HinputsI, hamuts: HamutsBox, structTT: StructIT[cI]): StructDefinitionI = {
    hinputs.lookupStruct(structTT.id)
  }

  def lookupInterface(hinputs: HinputsI, hamuts: HamutsBox, interfaceTT: InterfaceIT[cI]): InterfaceDefinitionI = {
    hinputs.lookupInterface(interfaceTT.id)
  }
}
