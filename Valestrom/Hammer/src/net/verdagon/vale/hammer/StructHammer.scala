package net.verdagon.vale.hammer

import net.verdagon.vale.hinputs.{Hinputs}
import net.verdagon.vale.metal.{Immutable => _, Mutable => _, Variability => _, Varying => _, _}
import net.verdagon.vale.{PackageCoordinate, vassert, vassertSome, vfail, metal => m}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.templata.{CoordTemplata, Export2, FunctionHeaderT}
import net.verdagon.vale.templar.types._

import scala.collection.immutable.ListMap

object StructHammer {
  val BOX_HUMAN_NAME = "__Box"
  val BOX_MEMBER_INDEX = 0
  val BOX_MEMBER_NAME = "__boxee"

  def translateInterfaces(hinputs: Hinputs, hamuts: HamutsBox): Unit = {
    hinputs.interfaces.foreach(interface => translateInterfaceRef(hinputs, hamuts, interface.getRef))
  }

  private def translateInterfaceRefs(
      hinputs: Hinputs,
    hamuts: HamutsBox,
      interfaceRefs2: List[InterfaceRefT]):
  (List[InterfaceRefH]) = {
    interfaceRefs2 match {
      case Nil => Nil
      case head2 :: tail2 => {
        val (headH) = translateInterfaceRef(hinputs, hamuts, head2)
        val (tailH) = translateInterfaceRefs(hinputs, hamuts, tail2)
        (headH :: tailH)
      }
    }
  }

  def translateInterfaceMethods(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      interfaceRef2: InterfaceRefT) = {

    val edgeBlueprint = hinputs.edgeBlueprintsByInterface(interfaceRef2);

    val methodsH =
      edgeBlueprint.superFamilyRootBanners.map(superFamilyRootBanner => {
        val header = hinputs.lookupFunction(superFamilyRootBanner.toSignature).get.header
        val prototypeH = FunctionHammer.translatePrototype(hinputs, hamuts, header.toPrototype)
        val virtualParamIndex = header.params.indexWhere(_.virtuality.nonEmpty)
        InterfaceMethodH(prototypeH, virtualParamIndex)
      })

    methodsH
  }

  def translateInterfaceRef(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    interfaceRef2: InterfaceRefT):
  InterfaceRefH = {
    hamuts.interfaceRefs.get(interfaceRef2) match {
      case Some(structRefH) => structRefH
      case None => {
        val fullNameH = NameHammer.translateFullName(hinputs, hamuts, interfaceRef2.fullName)
        // This is the only place besides InterfaceDefinitionH that can make a InterfaceRefH
        val temporaryInterfaceRefH = InterfaceRefH(fullNameH);
        hamuts.forwardDeclareInterface(interfaceRef2, temporaryInterfaceRefH)
        val interfaceDef2 = hinputs.lookupInterface(interfaceRef2);


        val methodsH = translateInterfaceMethods(hinputs, hamuts, interfaceRef2)

        val maybeExport = interfaceDef2.attributes.collectFirst { case Export2(packageCoord) => packageCoord }

        val interfaceDefH =
          InterfaceDefinitionH(
            fullNameH,
            maybeExport.nonEmpty,
            interfaceDef2.weakable,
            Conversions.evaluateMutability(interfaceDef2.mutability),
            List.empty /* super interfaces */,
            methodsH)
        hamuts.addInterface(interfaceRef2, interfaceDefH)
        vassert(interfaceDefH.getRef == temporaryInterfaceRefH)

        maybeExport match {
          case None =>
          case Some(exportPackageCoord) => {
            val exportedName =
              interfaceRef2.fullName.last match {
                case CitizenNameT(humanName, _) => humanName
                case _ => vfail("Can't export something that doesn't have a human readable name!")
              }
            hamuts.addKindExport(interfaceDefH.getRef, exportPackageCoord, exportedName)
          }
        }

        (interfaceDefH.getRef)
      }
    }
  }

  def translateStructs(hinputs: Hinputs, hamuts: HamutsBox): Unit = {
    hinputs.structs.foreach(structDef2 => translateStructRef(hinputs, hamuts, structDef2.getRef))
  }

  def translateStructRef(
      hinputs: Hinputs,
      hamuts: HamutsBox,
      structRef2: StructRefT):
  (StructRefH) = {
    hamuts.structRefsByRef2.get(structRef2) match {
      case Some(structRefH) => structRefH
      case None => {
        val (fullNameH) = NameHammer.translateFullName(hinputs, hamuts, structRef2.fullName)
        // This is the only place besides StructDefinitionH that can make a StructRefH
        val temporaryStructRefH = StructRefH(fullNameH);
        hamuts.forwardDeclareStruct(structRef2, temporaryStructRefH)
        val structDef2 = hinputs.lookupStruct(structRef2);
        val (membersH) =
          TypeHammer.translateMembers(hinputs, hamuts, structDef2.fullName, structDef2.members)

        val (edgesH) = translateEdgesForStruct(hinputs, hamuts, temporaryStructRefH, structRef2)

        // Make sure there's a destructor for this shared struct.
        structDef2.mutability match {
          case MutableT => None
          case ImmutableT => {
            if (structRef2 != Program2.emptyTupleStructRef) {
              vassertSome(
                hinputs.functions.find(function => {
                  function.header.fullName == FullNameT(PackageCoordinate.BUILTIN, List.empty, ImmConcreteDestructorNameT(structRef2))
                }))
            }
          }
        }

        val maybeExport = structDef2.attributes.collectFirst { case Export2(packageCoord) => packageCoord }

        val structDefH =
          StructDefinitionH(
            fullNameH,
            maybeExport.nonEmpty,
            structDef2.weakable,
            Conversions.evaluateMutability(structDef2.mutability),
            edgesH,
            membersH);
        hamuts.addStructOriginatingFromTemplar(structRef2, structDefH)
        vassert(structDefH.getRef == temporaryStructRefH)

        maybeExport match {
          case None =>
          case Some(exportPackageCoord) => {
            val exportedName =
              structRef2.fullName.last match {
                case CitizenNameT(humanName, _) => humanName
                case _ => vfail("Can't export something that doesn't have a human readable name!")
              }
            hamuts.addKindExport(structDefH.getRef, exportPackageCoord, exportedName)
          }
        }

        (structDefH.getRef)
      }
    }
  }

  def makeBox(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    conceptualVariability: VariabilityT,
    type2: CoordT,
    typeH: ReferenceH[KindH]):
  (StructRefH) = {
    val boxFullName2 = FullNameT(PackageCoordinate.BUILTIN, List.empty, CitizenNameT(BOX_HUMAN_NAME, List(CoordTemplata(type2))))
    val boxFullNameH = NameHammer.translateFullName(hinputs, hamuts, boxFullName2)
    hamuts.structDefsByRef2.find(_._2.fullName == boxFullNameH) match {
      case Some((_, structDefH)) => (structDefH.getRef)
      case None => {
        val temporaryStructRefH = StructRefH(boxFullNameH);

        // We don't actually care about the given variability, because even if it's final, we still need
        // the box to contain a varying reference, see VCBAAF.
        val _ = conceptualVariability
        val actualVariability = VaryingT

        val memberH =
          StructMemberH(
            NameHammer.addStep(hamuts, temporaryStructRefH.fullName, BOX_MEMBER_NAME),
            Conversions.evaluateVariability(actualVariability), typeH)

        val structDefH =
          StructDefinitionH(
            boxFullNameH,
            false,
            false,
            m.Mutable,
            List.empty,
            List(memberH));
        hamuts.addStructOriginatingFromHammer(structDefH)
        vassert(structDefH.getRef == temporaryStructRefH)
        (structDefH.getRef)
      }
    }
  }

  private def translateEdgesForStruct(
      hinputs: Hinputs, hamuts: HamutsBox,
      structRefH: StructRefH,
      structRef2: StructRefT):
  (List[EdgeH]) = {
    val edges2 = hinputs.edges.filter(_.struct == structRef2)
    translateEdgesForStruct(hinputs, hamuts, structRefH, edges2.toList)
  }

  private def translateEdgesForStruct(
      hinputs: Hinputs, hamuts: HamutsBox,
      structRefH: StructRefH,
      edges2: List[EdgeT]):
  (List[EdgeH]) = {
    edges2 match {
      case Nil => Nil
      case headEdge2 :: tailEdges2 => {
        val interfaceRef2 = headEdge2.interface
        val (headEdgeH) = translateEdge(hinputs, hamuts, structRefH, interfaceRef2, headEdge2)
        val (tailEdgesH) = translateEdgesForStruct(hinputs, hamuts, structRefH, tailEdges2)
        (headEdgeH :: tailEdgesH)
      }
    }
  }


  private def translateEdge(hinputs: Hinputs, hamuts: HamutsBox, structRefH: StructRefH, interfaceRef2: InterfaceRefT, edge2: EdgeT):
  (EdgeH) = {
    // Purposefully not trying to translate the entire struct here, because we might hit a circular dependency
    val interfaceRefH = translateInterfaceRef(hinputs, hamuts, interfaceRef2)
    val interfacePrototypesH = translateInterfaceMethods(hinputs, hamuts, interfaceRef2)
    val (prototypesH) = FunctionHammer.translatePrototypes(hinputs, hamuts, edge2.methods)
    val structPrototypesByInterfacePrototype = ListMap[InterfaceMethodH, PrototypeH](interfacePrototypesH.zip(prototypesH) : _*)
    (EdgeH(structRefH, interfaceRefH, structPrototypesByInterfacePrototype))
  }
}
