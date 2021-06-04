
#include "midasfunctions.h"
#include "region/iregion.h"
#include "region/linear/linear.h"

//void declareExtraInterfaceMethod(
//    GlobalState* globalState,
//    InterfaceKind* kind,
//    InterfaceMethod* newMethod,
//    std::function<Prototype*(StructKind*)> declarer,
//    std::function<void(StructKind*, Prototype*)> bodyGenerator) {
//  auto program = globalState->program;
//
//  std::vector<Prototype*> substructPrototypes;
//
//  globalState->addInterfaceExtraMethod(kind, newMethod);
//
//  for (auto nameAndStruct : globalState->program->structs) {
//    for (auto edge : nameAndStruct.second->edges) {
//      if (edge->interfaceName == kind) {
//        auto substruct = edge->structName;
//
//        Reference *substructReference = nullptr;
//        assert(false); // TODO
//        Prototype *substructPrototype = nullptr;
//        assert(false); // TODO
//        LLVMTypeRef functionLT = nullptr;
//        assert(false); // TODO
//        // globalState->region->translateType(substructReference).c_str()
//
//        globalState->addEdgeExtraMethod(edge, newMethod, substructPrototype);
//
//        auto functionL = declarer(substructPrototype);
//        assert(globalState->extraFunctions.find(substructPrototype) != globalState->extraFunctions.end());
//
//        substructPrototypes.push_back(substructPrototype);
//      }
//    }
//  }
//
//  for (auto substructPrototype : substructPrototypes) {
//    auto substruct =
//        dynamic_cast<StructKind*>(
//            substructPrototype->params[newMethod->virtualParamIndex]->kind);
//    assert(substruct);
//    bodyGenerator(substruct, substructPrototype);
//  }
//}
