
#ifndef VALE_INSTRUCTIONS_H_
#define VALE_INSTRUCTIONS_H_

class Expression;
class ConstantI64;
class ConstantVoid;
class ConstantBool;
class ConstantStr;
class ConstantF64;
class Argument;
class Stackify;
class Unstackify;
class Destructure;
class StructToInterfaceUpcast;
class InterfaceToInterfaceUpcast;
class Unreachable;
class LocalStore;
class LocalLoad;
class MemberStore;
class MemberLoad;
class NewArrayFromValues;
class KnownSizeArrayStore;
class UnknownSizeArrayStore;
class UnknownSizeArrayLoad;
class KnownSizeArrayLoad;
class Call;
class InterfaceCall;
class If;
class While;
class InlineBlock;
class Block;
class Return;
class ConstructUnknownSizeArray;
class DestroyKnownSizeArray;
class DestroyUnknownSizeArray;
class NewStruct;
class ArrayLength;
class CheckRefCount;
class Discard;
class Expression;
class IRegister;
class ReferenceRegister;
class AddressRegister;
class Local;
class VariableId;
class StackHeight;

enum class RefCountCategory {
    VARIABLE_REF_COUNT,
    MEMBER_REF_COUNT,
    REGISTER_REF_COUNT
};

class Expression {
public:
    virtual ~Expression() {}

//    virtual Reference* getResultType() const = 0;
};

class ConstantI64 : public Expression {
public:
  int value;

  ConstantI64(
      int value_)
      : value(value_) {}
};

class ConstantBool : public Expression {
public:
  bool value;

  ConstantBool(
      bool value_)
      : value(value_) {}
};


class ConstantVoid : public Expression {
public:
};


class ConstantStr : public Expression {
public:
  std::string value;
};


class ConstantF64 : public Expression {
public:
  float value;
};


class Argument : public Expression {
public:
  Reference* resultType;
  int argumentIndex;
  Argument(
      Reference* resultType_,
      int argumentIndex_) :
      resultType(resultType_),
    argumentIndex(argumentIndex_) {}
};


class Stackify : public Expression {
public:
  Expression* sourceExpr;
  Local* local;
  std::string maybeName;

  Stackify(
      Expression* sourceExpr_,
    Local* local_,
    std::string maybeName_) :
      sourceExpr(sourceExpr_),
    local(local_),
        maybeName(maybeName_){}
};


class Unstackify : public Expression {
public:
  Local* local;

  Unstackify(Local* local_) : local(local_){}
};


class Destructure : public Expression {
public:
  Expression* structExpr;
  std::vector<Reference*> localTypes;
  std::vector<Local*> localIndices;
};


class StructToInterfaceUpcast : public Expression {
public:
  Expression* sourceExpr;
  InterfaceRef* targetInterfaceRef;
};

class InterfaceToInterfaceUpcast : public Expression {
public:
  Expression* sourceExpr;
  InterfaceRef* targetInterfaceRef;
};

class Unreachable : public Expression {
public:
};

class LocalStore : public Expression {
public:
  Local* local;
  Expression* sourceExpr;
  std::string localName;

  LocalStore(
      Local* local_,
      Expression* sourceExpr_,
      std::string localName_) :
      local(local_),
      sourceExpr(sourceExpr_),
      localName(localName_) {}
};


class LocalLoad : public Expression {
public:
  Local* local;
  Ownership targetOwnership;
  std::string localName;

  LocalLoad(
      Local* local,
      Ownership targetOwnership,
      std::string localName) :
      local(local),
    targetOwnership(targetOwnership),
        localName(localName) {}
};


class MemberStore : public Expression {
public:
  Expression* structExpr;
  int memberIndex;
  Expression* sourceExpr;
  std::string memberName;
};


class MemberLoad : public Expression {
public:
  Expression* structExpr;
  StructReferend* structId;
  int memberIndex;
  Ownership targetOwnership;
  Reference* expectedMemberType;
  Reference* expectedResultType;
  std::string memberName;

  MemberLoad(
      Expression* structExpr_,
      StructReferend* structId_,
      int memberIndex_,
      Ownership targetOwnership_,
      Reference* expectedMemberType_,
      Reference* expectedResultType_,
      std::string memberName_) :
    structExpr(structExpr_),
    structId(structId_),
    memberIndex(memberIndex_),
    targetOwnership(targetOwnership_),
    expectedMemberType(expectedMemberType_),
    expectedResultType(expectedResultType_),
    memberName(memberName_) {}
};


class NewArrayFromValues : public Expression {
public:
  std::vector<Expression*> sourceExprs;
  Reference* arrayRefType;
};


class KnownSizeArrayStore : public Expression {
public:
  Expression* arrayExpr;
  Expression* indexExpr;
  Expression* sourceExpr;
};


class UnknownSizeArrayStore : public Expression {
public:
  Expression* arrayExpr;
  Expression* indexExpr;
  Expression* sourceExpr;
};


class UnknownSizeArrayLoad : public Expression {
public:
  Expression* arrayExpr;
  Expression* indexExpr;
  Reference* resultType;
  Ownership targetOwnership;
};


class KnownSizeArrayLoad : public Expression {
public:
  Expression* arrayExpr;
  Expression* indexExpr;
  Reference* resultType;
  Ownership targetOwnership;
};


class Call : public Expression {
public:
    Prototype *function;
    std::vector<Expression *> argExprs;

    Call(
        Prototype *function_,
        std::vector<Expression *> argExprs_)
        : function(function_),
          argExprs(argExprs_) {}
};

class ExternCall : public Expression {
public:
    Prototype *function;
    std::vector<Expression *> argExprs;

    ExternCall(
        Prototype *function_,
        std::vector<Expression *> argExprs_)
        : function(function_),
        argExprs(argExprs_) {}
};


class InterfaceCall : public Expression {
public:
  std::vector<Expression*> argExprs;
  int virtualParamIndex;
  InterfaceRef* interfaceRef;
  int indexInEdge;
  Prototype* functionType;
};


class If : public Expression {
public:
  Expression * conditionExpr;
  Expression * thenExpr;
  Expression * elseExpr;
  Reference * commonSupertype;

  If(
      Expression * conditionExpr_,
      Expression * thenExpr_,
      Expression * elseExpr_,
      Reference * commonSupertype_) :
    conditionExpr(conditionExpr_),
    thenExpr(thenExpr_),
    elseExpr(elseExpr_),
    commonSupertype(commonSupertype_) {}
};

class While : public Expression {
public:
  Expression* bodyExpr;

  While(Expression* bodyExpr_) : bodyExpr(bodyExpr_) {}
};

class Block : public Expression {
public:
    std::vector<Expression *> exprs;

    Block(
        std::vector<Expression *> exprs_) :
    exprs(exprs_) {}
};

class Return : public Expression {
public:
    Expression *sourceExpr;

    Return(
        Expression *sourceExpr_)
        : sourceExpr(sourceExpr_) {}
};


class ConstructUnknownSizeArray : public Expression {
public:
  Expression* sizeExpr;
  Expression* generatorExpr;
  Reference* arrayRefType;
};

class DestroyKnownSizeArray : public Expression {
public:
  Expression* arrayExpr;
  Expression* consumerExpr;
};

class DestroyUnknownSizeArray : public Expression {
public:
  Expression* arrayExpr;
  Expression* consumerExpr;
};

class NewStruct : public Expression {
public:
  std::vector<Expression*> sourceExprs;
  Reference* resultType;

  NewStruct(
      std::vector<Expression*> sourceExprs_,
      Reference* resultType_) :
      sourceExprs(sourceExprs_),
      resultType(resultType_) {}
};

class ArrayLength : public Expression {
public:
  Expression* sourceExpr;
};


class CheckRefCount : public Expression {
public:
  Expression* refExpr;
  RefCountCategory category;
  Expression* numExpr;
};


class Discard : public Expression {
public:
  Expression* sourceExpr;
  Reference* sourceResultType;

  Discard(Expression* sourceExpr_, Reference* sourceResultType_) :
      sourceExpr(sourceExpr_), sourceResultType(sourceResultType_) {}
};

class Local {
public:
    VariableId* id;
    Reference* type;

    Local(
        VariableId* id_,
        Reference* type_) :
    id(id_),
    type(type_) {}
};

class VariableId {
public:
    int number;
    std::string maybeName;

    VariableId(
        int number_,
    std::string maybeName_) :
    number(number_),
    maybeName(maybeName_) {}
};

#endif