// Catalyst.java
package catalyst;

import java.io.*;
import java.util.*;
import org.json.simple.*;
import org.json.simple.parser.*;

public class Catalyst {
    private static Integer knownLiveChanges = 0;
    private static HashMap<String, FunctionMaps> functionInfo;
    private static HashMap<String, JSONObject> functionObjects;
    private static HashMap<String, Struct> structInfo;
 
    private static class Struct {
        public Ref[] Members;

        public Struct(Ref[] members) {
            this.Members = members;
        }
    }

    private static void printStructInfo(HashMap<String, Struct> structs) {
        for (String name : structs.keySet()) {
            Ref[] members = structs.get(name).Members;
            System.out.println(name + ": ");
            for (Ref mem : members) {
                System.out.println("\t" + mem.Name);
            }
        }
    }
    public static void main(String[] args) {
        if (args.length == 1) {
            catalyst.TestCatalyst.runTests();
            return;
        }
        // Parse JSON AST
        JSONParser parser = new JSONParser();
        try {
            JSONObject program = (JSONObject)parser.parse(new FileReader(args[0]));
            JSONArray functions = (JSONArray)program.get("functions");
            JSONArray structsArr = (JSONArray)program.get("structs");
            functionInfo = new HashMap<String, FunctionMaps>();
            functionObjects = new HashMap<String, JSONObject>();
            structInfo = parseStructs(structsArr);
            //printStructInfo(structInfo);

            // Get function objects
            for (Object func : functions) {
                JSONObject f = (JSONObject)func;
                JSONObject p = (JSONObject)f.get("prototype");
                String name = (String)p.get("name");
                functionObjects.put(name, f);
            }

            // Parse functions
            for (String name : functionObjects.keySet()) {
                if (!functionInfo.containsKey(name)) {
                    FunctionMaps fmaps = new FunctionMaps(name);
                    parseFunction(functionObjects.get(name), fmaps);
                }
            }

            System.out.println("Changed " + knownLiveChanges.toString() + " known lives");
            knownLiveChanges = 0;
            // write to output file
            Writer out = new FileWriter(args[1]);
            String output = program.toJSONString();
            out.write(output);
            out.close();

        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private static HashMap<String, Struct> parseStructs(JSONArray structsArr) {
        int arrSize = structsArr.size();
        HashMap<String, Struct> structs = new HashMap<String, Struct>();
        for (int i = 0; i < arrSize; i++) {
            JSONObject s = (JSONObject)structsArr.get(i);
            String sName = (String)s.get("name");
            JSONArray membersArr = (JSONArray)s.get("members");
            int memSize = membersArr.size();
            Ref[] members = new Ref[memSize];
            
            for (int j = 0; j < memSize; j++) {
                JSONObject m = (JSONObject)membersArr.get(j);
                String name = (String)m.get("name");
                String var = getType((JSONObject)m.get("variability"));
                String own = getOwnership(m);
                Optional<String> structName = getReferendStructName(m);
                members[j] = new Ref(name, var, own, structName);
            }

            structs.put(sName, new Struct(members));
        }

        return structs;
    }

    private static Ref[] getMemberInfo(Optional<String> structName) {
        if (structName.isPresent()) {
            return structInfo.get(structName.get()).Members;
        }
        return new Ref[0];
    }


    private static Optional<String> getReferendStructName(JSONObject obj) {
        JSONObject referend = getReferend(obj);
        Optional<String> structName = Optional.empty();

        if (getType(referend).equals("StructId")) {
            structName = Optional.of((String)referend.get("name"));
        }

        return structName;
    }

    private static JSONObject getReferend(JSONObject obj) {
        JSONObject type = (JSONObject)obj.get("type");
        return (JSONObject)type.get("referend");
    }

    private static String getType(JSONObject obj) {
        return (String)obj.get("__type");
    }

    private static Long getRefId(JSONObject obj) {
        JSONObject id = (JSONObject)obj.get("id");
        return (Long)id.get("number");
    }

    private static String getOwnership(JSONObject obj) {
        JSONObject type = (JSONObject)obj.get("type");
        JSONObject ownership = (JSONObject)type.get("ownership");
        return getType(ownership);
    }

    private static void parseFunction(JSONObject f, FunctionMaps fmaps) {
        JSONObject block = (JSONObject)f.get("block");
        JSONObject proto = (JSONObject)f.get("prototype");

        // get return value info
        JSONObject ret = (JSONObject)proto.get("return");
        String retOwnership = getType((JSONObject)ret.get("ownership"));
        Optional<String> retStructName = Optional.empty();
        if (getType((JSONObject)ret.get("referend")).equals("StructId")) {
            retStructName = Optional.of((String)((JSONObject)ret.get("referend")).get("name"));
        }

        JSONArray args = (JSONArray)proto.get("params");
        Long argId = Long.valueOf(-1);

        // add arguments as variables
        for (Object a : args) {
            // create object and members
            JSONObject arg = (JSONObject)a;
            String ownership = getType((JSONObject)arg.get("ownership"));
            JSONObject referend = (JSONObject)arg.get("referend");
            Optional<String> structName = Optional.empty();
            if (getType(referend).equals("StructId")) {
                structName = Optional.of((String)referend.get("name"));
            }
            OptionalLong objId = createObject(structName, fmaps);

            // add unnamed variable with negative identifier for accessing newly created object
            fmaps.addVariable(argId, new Ref("", "Final", ownership, structName), objId);
            argId--;
        }

        OptionalLong returnObj = parseBlock(block, fmaps);
        fmaps.setReturnInfo(returnObj, argId, retStructName, retOwnership);
        functionInfo.put(fmaps.FunctionName, fmaps);

        // System.out.println("PARSED FUNCTION: " + fmaps.FunctionName);
        // System.out.print("Members as args: [");
        // for (int i=0; i<fmaps.Returns.MemberArgIdxs.length; i++) {
        //     System.out.print(fmaps.Returns.MemberArgIdxs[i].toString() + ", " );
        // }
        // System.out.println("]");
    }

    private static OptionalLong createObject(Optional<String> structName, FunctionMaps fmaps) {
        if (!structName.isPresent()) {
            return OptionalLong.empty();
        }

        Ref[] memberInfo = getMemberInfo(structName);
        StructMember[] members = new StructMember[memberInfo.length];

        for (int i=0; i<memberInfo.length; i++) {
            members[i] = new StructMember(createObject(memberInfo[i].StructName, fmaps), memberInfo[i].Ownership);
        }

        return OptionalLong.of(fmaps.addObject(members, false));
    }

    private static OptionalLong parseBlock(JSONObject block, FunctionMaps fmaps) {
        JSONObject innerExpr = (JSONObject)block.get("innerExpr");

        if (innerExpr == null) {
            return parseExpr(block, fmaps);
        } else {
            return parseExpr(innerExpr, fmaps);
        }
    }

    private static OptionalLong parseConsecutor(JSONObject obj, FunctionMaps fmaps) {
        JSONArray exprs = (JSONArray)obj.get("exprs");
        OptionalLong l = OptionalLong.empty();

        for (Object ex : exprs) {
            JSONObject e = (JSONObject)ex;
            l = parseExpr(e, fmaps);
        }

        return l;
    }
    
    private static OptionalLong parseExpr(JSONObject e, FunctionMaps fmaps) {
        String type = getType(e);
        switch (type) {
            case "Argument":
                return parseArgument(e, fmaps);
            case "ConstantI64":
            case "ConstantBool":
            case "ConstantStr":
            case "ConstantF64":
                return OptionalLong.empty();
            case "Destroy":
                parseDestroy(e, fmaps);
                return OptionalLong.empty();
            case "DestroyKnownSizeArrayIntoLocals":
            case "DestroyKnownSizeArrayIntoFunction":
            case "DestroyUnknownSizeArray":
                // TODO parse into source
                return OptionalLong.empty();
            case "Stackify":
                return parseStackify(e, fmaps);
            case "Unstackify":
                return parseUnstackify(e, fmaps);
            case "StructToInterfaceUpcast":
            case "InterfaceToInterfaceUpcast":
                return parseSource(e, fmaps);
            case "LocalStore":
                // TODO
                return OptionalLong.empty();
            case "LocalLoad":
                return parseLocalLoad(e, fmaps);
            case "MemberStore":
                return parseMemberStore(e, fmaps);
            case "MemberLoad":
                return parseMemberLoad(e, fmaps);
            case "KnownSizeArrayStore":
            case "UnknownSizeArrayStore":
            case "KnownSizeArrayLoad":
            case "UnknownSizeArrayLoad":
                return OptionalLong.empty();
            case "NewStruct":
            case "NewArrayFromValues":
            case "ConstructUnknownSizeArray":
                return parseConstructor(e, fmaps);
            case "Call":
                return parseCall(e, fmaps);
            case "ExternCall":
            case "InterfaceCall":
                return OptionalLong.empty();
            case "If":
                return parseIf(e, fmaps);
            case "While":
                return parseWhile(e, fmaps);
            case "Consecutor":
                return parseConsecutor(e, fmaps);
            case "Block":
                // TODO Clear locals introduced in block
                return parseBlock(e, fmaps);
            case "Return":
                return parseSource(e, fmaps);
            case "WeakAlias": // TODO
            case "LockWeak": // TODO
            case "ArrayLength":
            case "CheckRefCount":
                return OptionalLong.empty();
            case "Discard":
                // TODO: empty? what about source expr?
                return parseSource(e, fmaps);
            default:
                return OptionalLong.empty();
        }
    } 

    private static OptionalLong parseIf(JSONObject obj, FunctionMaps fmaps) {
        JSONObject condBlock = (JSONObject)obj.get("conditionBlock");
        JSONObject thenBlock = (JSONObject)obj.get("thenBlock");
        JSONObject elseBlock = (JSONObject)obj.get("elseBlock");

        parseBlock(condBlock, fmaps);
        parseBlock(thenBlock, fmaps);
        parseBlock(elseBlock, fmaps);

        return OptionalLong.empty();
    }

    private static OptionalLong parseWhile(JSONObject obj, FunctionMaps fmaps) {
        JSONObject bodyBlock = (JSONObject)obj.get("bodyBlock");

        parseBlock(bodyBlock, fmaps);

        return OptionalLong.empty();
    }

    private static OptionalLong parseConstructor(JSONObject obj, FunctionMaps fmaps) {
        JSONArray sourceExprs = (JSONArray)obj.get("sourceExprs");
        JSONObject resType = (JSONObject)obj.get("resultType");
        JSONObject referend = (JSONObject)resType.get("referend");
        Optional<String> structName = Optional.empty();

        if (getType(referend).equals("StructId")) {
            structName = Optional.of((String)referend.get("name"));
        }

        Ref[] memberInfo = getMemberInfo(structName);
        StructMember[] members = new StructMember[memberInfo.length];

        for (int i=0; i<memberInfo.length; i++) {
            members[i] = new StructMember(parseExpr((JSONObject)sourceExprs.get(i), fmaps), memberInfo[i].Ownership);
        }
        return OptionalLong.of(fmaps.addObject(members, false));
    }

    private static OptionalLong parseArgument(JSONObject obj, FunctionMaps fmaps) {
        long argIdx = (long)obj.get("argumentIndex");
        long varIdx = (-1*argIdx) - 1;

        return fmaps.getObject(Long.valueOf(varIdx));
    }

    private static OptionalLong parseCall(JSONObject obj, FunctionMaps fmaps) {
        JSONObject fun = (JSONObject)obj.get("function");
        String fname = (String)fun.get("name");
        JSONArray argExprs = (JSONArray)obj.get("argExprs");
        OptionalLong[] argEvals = new OptionalLong[argExprs.size()];

        // Parse function if it has not already been parsed
        // get info on returned value
        if (!functionInfo.containsKey(fname)) {
            FunctionMaps newFmaps = new FunctionMaps(fname);
            parseFunction(functionObjects.get(fname), newFmaps);
        }

        // Parse argument expressions
        for (int i=0; i<argEvals.length; i++) {
            argEvals[i] = parseExpr((JSONObject)argExprs.get(i), fmaps);
        }

        ReturnInfo retInfo = functionInfo.get(fname).Returns;
        StructMember[] members = new StructMember[retInfo.MemberArgIdxs.length];
        Ref[] memberInfo = getMemberInfo(retInfo.StructName);

        for (int j=0; j<members.length; j++) {
            members[j] = new StructMember(OptionalLong.empty(), memberInfo[j].Ownership);

            if (retInfo.MemberArgIdxs[j].isPresent()) {
                // If member is an argument
                members[j].Id = argEvals[j];
            }
        }

        return OptionalLong.of(fmaps.addObject(members, true));
    }

    private static OptionalLong parseMemberLoad(JSONObject obj, FunctionMaps fmaps) {
        JSONObject structExpr = (JSONObject)obj.get("structExpr");
        OptionalLong structObjId = parseExpr(structExpr, fmaps);
        Long memberIdx = (Long)obj.get("memberIndex");
        setKnownLiveTrue(obj, structObjId, fmaps, "structKnownLive");

        return fmaps.getMemberObj(structObjId, memberIdx);
    }

    private static OptionalLong parseMemberStore(JSONObject obj, FunctionMaps fmaps) {
        JSONObject structExpr = (JSONObject)obj.get("structExpr");
        JSONObject srcExpr = (JSONObject)obj.get("sourceExpr");
        Long memberIdx = (Long)obj.get("memberIndex");
        OptionalLong srcId = parseExpr(srcExpr, fmaps);
        OptionalLong structObjId = parseExpr(structExpr, fmaps);

        setKnownLiveTrue(obj, structObjId, fmaps, "structKnownLive");

        // Set member to OptionalLong returned from source
        fmaps.setMember(structObjId, memberIdx, srcId);

        // Return old member object
        return structObjId;
    }

    private static void setKnownLiveTrue(JSONObject obj, OptionalLong objId, FunctionMaps fmaps, String klType) {
        if (fmaps.isAlive(objId)) {
            if (obj.containsKey(klType)){
                obj.put(klType, true);
                knownLiveChanges++;
            }
        }
    }

    // returns object ID if it exists
    private static OptionalLong parseLocalLoad(JSONObject obj, FunctionMaps fmaps) {
        JSONObject local = (JSONObject)obj.get("local");
        Long refId = getRefId(local);

        return fmaps.getObject(refId);
    }

    private static OptionalLong parseSource(JSONObject obj, FunctionMaps fmaps) {
        JSONObject sourceExpr = (JSONObject)obj.get("sourceExpr");
        return parseExpr(sourceExpr, fmaps);
    }

    private static OptionalLong parseStackify(JSONObject obj, FunctionMaps fmaps) {
        JSONObject sourceExpr = (JSONObject)obj.get("sourceExpr");
        JSONObject local = (JSONObject)obj.get("local");
        OptionalLong objId = parseExpr(sourceExpr, fmaps);

        // Get variable information
        Long varId = getRefId(local);
        // TODO: perhaps check if optName is of type "None"
        String varName = (String)((JSONObject)obj.get("optName")).get("value");
        String ownership = getOwnership(local);
        String variability = getType((JSONObject)local.get("variability"));
        Optional<String> structName = getReferendStructName(local);

        fmaps.addVariable(varId, new Ref(varName, variability, ownership, structName), objId);

        //fmaps.printMaps();
        
        return OptionalLong.empty();
    }

    private static OptionalLong parseUnstackify(JSONObject obj, FunctionMaps fmaps) {
        JSONObject local = (JSONObject)obj.get("local");
        Long refId = getRefId(local);
        OptionalLong ret = fmaps.getObject(refId);
        fmaps.killRef(refId);

        return ret;
    }

    private static void parseDestroy(JSONObject obj, FunctionMaps fmaps) {
        //TODO: Deal with members
        JSONArray locals = (JSONArray)obj.get("localIndices");

        for (Object loc : locals) {
            JSONObject l = (JSONObject)loc;
            Long refId = getRefId(l);
            String ownership = getOwnership(l);

            //addRefToTables(refId, ownership, OptionalLong.empty(), new OptionalLong[0], fmaps);
        }
    }
}

