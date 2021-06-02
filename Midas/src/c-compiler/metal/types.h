
#ifndef VALE_TYPES_H_
#define VALE_TYPES_H_

#include <string>
#include <vector>
#include <cassert>

#include "name.h"

// Defined elsewhere
class Name;
class PackageCoordinate;
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
class StaticSizedArrayT;
class RuntimeSizedArrayT;

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
  PackageCoordinate* packageCoord;
  std::string id;

  RegionId(PackageCoordinate* packageCoord_, std::string id_) :
      packageCoord(packageCoord_), id(id_) {}
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
    virtual PackageCoordinate* getPackageCoordinate() const = 0;
};

class Int : public Referend {
public:
  RegionId* regionId;

  Int(RegionId* regionId_) :
      regionId(regionId_) {}

  PackageCoordinate* getPackageCoordinate() const override { return regionId->packageCoord; }
};

class Bool : public Referend {
public:
  RegionId* regionId;

  Bool(RegionId* regionId_) :
      regionId(regionId_) {}

  PackageCoordinate* getPackageCoordinate() const override { return regionId->packageCoord; }
};

class Str : public Referend {
public:
  RegionId* regionId;

  Str(RegionId* regionId_) :
      regionId(regionId_) {}

  PackageCoordinate* getPackageCoordinate() const override { return regionId->packageCoord; }
};

class Float : public Referend {
public:
  RegionId* regionId;

  Float(RegionId* regionId_) :
      regionId(regionId_) {}

  PackageCoordinate* getPackageCoordinate() const override { return regionId->packageCoord; }
};

class Never : public Referend {
public:
  RegionId* regionId;

  Never(RegionId* regionId_) :
      regionId(regionId_) {}

  PackageCoordinate* getPackageCoordinate() const override { return regionId->packageCoord; }
};

class InterfaceReferend : public Referend {
public:
    Name* fullName;

  InterfaceReferend(Name* fullName_) :
      fullName(fullName_) {}

  PackageCoordinate* getPackageCoordinate() const override { return fullName->packageCoord; }

};

// Interned
class StructReferend : public Referend {
public:
    Name* fullName;

    StructReferend(Name* fullName_) :
        fullName(fullName_) {}

  PackageCoordinate* getPackageCoordinate() const override { return fullName->packageCoord; }
};

// Interned
class RawArrayT {
public:
  RegionId* regionId;
  Mutability mutability;
  Variability variability;
  Reference *elementType;

  RawArrayT(
      RegionId* regionId_,
      Mutability mutability_,
      Variability variability_,
      Reference* elementType_) :
      regionId(regionId_),
      mutability(mutability_),
      variability(variability_),
      elementType(elementType_) {}
};

// Interned
class StaticSizedArrayT : public Referend {
public:
  Name* name;

  StaticSizedArrayT(
      Name* name_) :
      name(name_) {}

  PackageCoordinate* getPackageCoordinate() const override { return name->packageCoord; }
};

class StaticSizedArrayDefinitionT {
public:
  Name* name;
  StaticSizedArrayT* referend;
  int size;
  RawArrayT* rawArray;

  StaticSizedArrayDefinitionT(
      Name* name_,
      StaticSizedArrayT* referend_,
      int size_,
      RawArrayT* rawArray_) :
      name(name_),
      referend(referend_),
      size(size_),
      rawArray(rawArray_) {}

};



// Interned
class RuntimeSizedArrayT : public Referend {
public:
  Name* name;

  RuntimeSizedArrayT(
      Name* name_) :
      name(name_) {}

  PackageCoordinate* getPackageCoordinate() const override { return name->packageCoord; }
};

class RuntimeSizedArrayDefinitionT {
public:
  Name* name;
  RuntimeSizedArrayT* referend;
  RawArrayT* rawArray;

  RuntimeSizedArrayDefinitionT(
      Name* name_,
      RuntimeSizedArrayT* referend_,
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