
#ifndef VALE_TYPES_H_
#define VALE_TYPES_H_

#include <string>
#include <vector>
#include <cassert>

// Defined elsewhere
class Name;
class CodeLocation;

// Defined in this file
class Reference;
class Referend;
class Int;
class Bool;
class Str;
class Void;
class Float;
class Never;
class InterfaceReferend;
class StructReferend;
class RawArrayT;
class KnownSizeArrayT;
class UnknownSizeArrayT;

enum class Ownership {
  OWN,
  BORROW,
  WEAK,
  SHARE
};

enum class Weakability {
  WEAKABLE,
  NON_WEAKABLE,
};

enum class Permission {
    READONLY,
    READWRITE,
    EXCLUSIVE_READWRITE
};

enum class Location {
    INLINE,
    YONDER
};

enum class Mutability {
    IMMUTABLE,
    MUTABLE
};

enum class Virtuality {
  NORMAL,
  ABSTRACT
};

enum class Variability {
    FINAL,
    VARYING
};

struct RegionId {
  std::string id;

  RegionId(std::string id_) :
      id(id_) {}
};

// Interned
class Reference {
public:
  Ownership ownership;
  Location location;
  Referend* referend;
//  std::string debugStr;

  Reference(
      Ownership ownership_,
      Location location_,
      Referend* referend_
//      , const std::string& debugStr_
  ) :
      ownership(ownership_),
      location(location_),
      referend(referend_)
//    , debugStr(debugStr_)
  {

    if (ownership == Ownership::BORROW || ownership == Ownership::WEAK) {
      assert(location == Location::YONDER);
    }
  }

  // Someday, have a nice way to print out this Reference...
  std::string str() { return ""; }
};

class Referend {
public:
    virtual ~Referend() {}
};

class Int : public Referend {
public:
  RegionId* regionId;

  Int(RegionId* regionId_) :
      regionId(regionId_) {}
};

class Bool : public Referend {
public:
  RegionId* regionId;

  Bool(RegionId* regionId_) :
      regionId(regionId_) {}
};

class Str : public Referend {
public:
  RegionId* regionId;

  Str(RegionId* regionId_) :
      regionId(regionId_) {}
};

class Float : public Referend {
public:
  RegionId* regionId;

  Float(RegionId* regionId_) :
      regionId(regionId_) {}
};

class Never : public Referend {
public:
  RegionId* regionId;

  Never(RegionId* regionId_) :
      regionId(regionId_) {}
};

class InterfaceReferend : public Referend {
public:
    Name* fullName;

  InterfaceReferend(Name* fullName_) :
      fullName(fullName_) {}

};

// Interned
class StructReferend : public Referend {
public:
    Name* fullName;

    StructReferend(Name* fullName_) :
        fullName(fullName_) {}
};

// Interned
class RawArrayT {
public:
  RegionId* regionId;
  Mutability mutability;
  Reference *elementType;

  RawArrayT(
      RegionId* regionId_,
      Mutability mutability_,
      Reference* elementType_) :
      regionId(regionId_),
      mutability(mutability_),
      elementType(elementType_) {}
};

// Interned
class KnownSizeArrayT : public Referend {
public:
  Name* name;

  KnownSizeArrayT(
      Name* name_) :
      name(name_) {}

};

class KnownSizeArrayDefinitionT {
public:
  Name* name;
  KnownSizeArrayT* referend;
  int size;
  RawArrayT* rawArray;

  KnownSizeArrayDefinitionT(
      Name* name_,
      KnownSizeArrayT* referend_,
      int size_,
      RawArrayT* rawArray_) :
      name(name_),
      referend(referend_),
      size(size_),
      rawArray(rawArray_) {}

};



// Interned
class UnknownSizeArrayT : public Referend {
public:
  Name* name;

  UnknownSizeArrayT(
      Name* name_) :
      name(name_) {}

};

class UnknownSizeArrayDefinitionT {
public:
  Name* name;
  UnknownSizeArrayT* referend;
  RawArrayT* rawArray;

  UnknownSizeArrayDefinitionT(
      Name* name_,
      UnknownSizeArrayT* referend_,
      RawArrayT* rawArray_) :
      name(name_),
      referend(referend_),
      rawArray(rawArray_) {}

};


class IContainer {
public:
    std::string humanName;
    CodeLocation* location;
};

#endif