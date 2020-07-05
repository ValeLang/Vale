package net.verdagon.vale.hammer

import net.verdagon.vale.hinputs.{ETable2, Hinputs, TetrisTable}
import net.verdagon.vale.metal.{Mutable => _, Immutable => _, Variability => _, Varying => _, _}
import net.verdagon.vale.{vassert, vassertSome, metal => m}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.templata.CoordTemplata
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
      interfaceRefs2: List[InterfaceRef2]):
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

  def translateInterfaceRef(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    interfaceRef2: InterfaceRef2):
  InterfaceRefH = {
    hamuts.interfaceRefs.get(interfaceRef2) match {
      case Some(structRefH) => structRefH
      case None => {
        val fullNameH = NameHammer.translateFullName(hinputs, hamuts, interfaceRef2.fullName)
        // This is the only place besides InterfaceDefinitionH that can make a InterfaceRefH
        val temporaryInterfaceRefH = InterfaceRefH(fullNameH);
        hamuts.forwardDeclareInterface(interfaceRef2, temporaryInterfaceRefH)
        val interfaceDef2 = hinputs.lookupInterface(interfaceRef2);


        val edgeBlueprint = hinputs.edgeBlueprintsByInterface(interfaceRef2);

        val methodsH =
          edgeBlueprint.superFamilyRootBanners.map(superFamilyRootBanner => {
            val header = hinputs.lookupFunction(superFamilyRootBanner.toSignature).get.header
            val prototypeH = FunctionHammer.translatePrototype(hinputs, hamuts, header.toPrototype)
            val virtualParamIndex = header.params.indexWhere(_.virtuality.nonEmpty)
            InterfaceMethodH(prototypeH, virtualParamIndex)
          })

        val interfaceDefH =
          InterfaceDefinitionH(
            fullNameH,
            Conversions.evaluateMutability(interfaceDef2.mutability),
            List() /* super interfaces */,
            methodsH)
        hamuts.addInterface(interfaceRef2, interfaceDefH)
        vassert(interfaceDefH.getRef == temporaryInterfaceRefH)
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
      structRef2: StructRef2):
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
          case Mutable => None
          case Immutable => {
            if (structRef2 != Program2.emptyTupleStructRef) {
              vassertSome(
                hinputs.functions.find(function => {
                  function.header.fullName == FullName2(List(), ImmConcreteDestructorName2(structRef2))
                }))
            }
          }
        }

        val structDefH =
          StructDefinitionH(
            fullNameH,
            structDef2.`export`,
            Conversions.evaluateMutability(structDef2.mutability),
            edgesH,
            membersH);
        hamuts.addStructOriginatingFromTemplar(structRef2, structDefH)
        vassert(structDefH.getRef == temporaryStructRefH)
        (structDefH.getRef)
      }
    }
  }

  def makeBox(hinputs: Hinputs, hamuts: HamutsBox, conceptualVariability: Variability, type2: Coord, typeH: ReferenceH[ReferendH]):
  (StructRefH) = {
    val boxFullName2 = FullName2(List(), CitizenName2(BOX_HUMAN_NAME, List(CoordTemplata(type2))))
    val boxFullNameH = NameHammer.translateFullName(hinputs, hamuts, boxFullName2)
    hamuts.structDefsByRef2.find(_._2.fullName == boxFullNameH) match {
      case Some((_, structDefH)) => (structDefH.getRef)
      case None => {
        val temporaryStructRefH = StructRefH(boxFullNameH);

        // We don't actually care about the given variability, because even if it's final, we still need
        // the box to contain a varying reference, see VCBAAF.
        val _ = conceptualVariability
        val actualVariability = Varying

        val memberH = StructMemberH(temporaryStructRefH.fullName.addStep(BOX_MEMBER_NAME), Conversions.evaluateVariability(actualVariability), typeH)

        val structDefH =
          StructDefinitionH(
            boxFullNameH,
            false,
            m.Mutable,
            List(),
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
      structRef2: StructRef2):
  (List[EdgeH]) = {
    val edges2 = hinputs.edges.filter(_.struct == structRef2)
    translateEdgesForStruct(hinputs, hamuts, structRefH, edges2.toList)
  }

  private def translateEdgesForStruct(
      hinputs: Hinputs, hamuts: HamutsBox,
      structRefH: StructRefH,
      edges2: List[Edge2]):
  (List[EdgeH]) = {
    edges2 match {
      case Nil => Nil
      case headEdge2 :: tailEdges2 => {
        val interfaceRef2 = headEdge2.interface
        val (interfaceRefH) = StructHammer.translateInterfaceRef(hinputs, hamuts, interfaceRef2)
        val interfaceDefH = hamuts.interfaceDefs(interfaceRef2)
        val (headEdgeH) = translateEdge(hinputs, hamuts, structRefH, interfaceDefH, headEdge2)
        val (tailEdgesH) = translateEdgesForStruct(hinputs, hamuts, structRefH, tailEdges2)
        (headEdgeH :: tailEdgesH)
      }
    }
  }


  private def translateEdge(hinputs: Hinputs, hamuts: HamutsBox, structRefH: StructRefH, interfaceDefH: InterfaceDefinitionH, edge2: Edge2):
  (EdgeH) = {
    val interfacePrototypesH = interfaceDefH.methods;
    val (prototypesH) = FunctionHammer.translatePrototypes(hinputs, hamuts, edge2.methods)
    val structPrototypesByInterfacePrototype = ListMap[InterfaceMethodH, PrototypeH](interfacePrototypesH.zip(prototypesH) : _*)
    (EdgeH(structRefH, interfaceDefH.getRef, structPrototypesByInterfacePrototype))
  }
}
