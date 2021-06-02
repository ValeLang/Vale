#ifndef METAL_NAME_H_
#define METAL_NAME_H_

#include <string>
#include <vector>

struct PackageCoordinate {
  std::string moduleName;
  std::vector<std::string> packageSteps;

  PackageCoordinate(
      const std::string& moduleName_,
      const std::vector<std::string>& packageSteps_) :
      moduleName(moduleName_),
      packageSteps(packageSteps_)
  {}

  struct StringVectorHasher {
    size_t operator()(const std::vector<std::string>& a) const {
      size_t hash = 0;
      for (int i = 0; i < a.size(); i++)
        hash = hash * 37 + std::hash<std::string>()(a[i]);
      return hash;
    }
  };
  struct StringVectorEquator {
    bool operator()(const std::vector<std::string>& a, const std::vector<std::string>& b) const {
      if (a.size() != b.size())
        return false;
      for (int i = 0; i < a.size(); i++)
        if (a[i] != b[i])
          return false;
      return true;
    }
  };

  struct Hasher {
    size_t operator()(const PackageCoordinate *a) const {
      size_t hash = 0;
      hash = hash * 37 + std::hash<std::string>()(a->moduleName);
      hash = hash * 41 + StringVectorHasher()(a->packageSteps);
      return hash;
    }
  };
  struct Equator {
    bool operator()(const PackageCoordinate* a, const PackageCoordinate* b) const {
      if (a->moduleName != b->moduleName)
        return false;
      if (!StringVectorEquator()(a->packageSteps, b->packageSteps))
        return false;
      return true;
    }
  };
};

class Name {
public:
  PackageCoordinate* packageCoord;
  std::string name;

  Name(PackageCoordinate* packageCoord_, const std::string& name_) :
      packageCoord(packageCoord_), name(name_) {}
};

#endif
