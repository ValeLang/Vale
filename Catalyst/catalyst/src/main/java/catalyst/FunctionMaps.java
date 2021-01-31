// FunctionMaps.java
package catalyst;

import java.util.*;

public class FunctionMaps {

    private static class LivenessInfo {
        public Boolean Liveness;
        public StructMember[] Members;

        public LivenessInfo(StructMember[] members, Boolean liveness) {
            this.Liveness = liveness;
            this.Members = members;
        }
    }

    private static class Variable {
        public OptionalLong ObjectId;
        public Ref Info;

        public Variable(OptionalLong id, Ref info) {
            this.ObjectId = id;
            this.Info = info;
        }
    }

    public String FunctionName;

    // Args and some objects aren't stackified and are identified by this 
    // unique decrementing number starting from -1
    public Long ObjCounter;

    public ReturnInfo Returns;
    
    // Stores objects references
    private HashMap<Long, LivenessInfo> Objects = new HashMap<Long, LivenessInfo>();

    // Stores variables references
    private HashMap<Long, Variable> Variables = new HashMap<Long, Variable>();

    public FunctionMaps(String funName) {
        this.FunctionName = funName;
        this.ObjCounter = Long.valueOf(0);
    }

    public Long addObject(StructMember[] members, Boolean liveness) {
        Objects.put(ObjCounter, new LivenessInfo(members, liveness));
        ObjCounter++;
        return ObjCounter - 1;
    }

    public void addVariable(Long id, Ref info, OptionalLong objectId) {
        Variables.put(id, new Variable(objectId, info));
    }

    public void killObj(Long objId) {
        LivenessInfo obj = Objects.get(objId);
        obj.Liveness = false;

        // Kill members if necessary
        for (Object m : obj.Members) {
            StructMember mem = (StructMember)m;
            if (mem.Ownership == "Own" && mem.Id.isPresent()) {
                // If object owns member, set member liveness to false
                killObj(mem.Id.getAsLong());
            }
        }
    }

    public void killRef(Long refId) {
        if (Variables.containsKey(refId)) {
            Variable var = Variables.get(refId);

            if (var.Info.Ownership.equals("Own") && var.ObjectId.isPresent()) {
                // If variable owns object, kill object
                killObj(var.ObjectId.getAsLong());
            }

            Variables.remove(refId);
        }
    }

    public Boolean isAlive(OptionalLong objId) {
        if (objId.isPresent() && Objects.containsKey(objId.getAsLong())) {
            return Objects.get(objId.getAsLong()).Liveness;
        }

        return false;
    }

    public OptionalLong getObject(Long refId) {
        if (Variables.containsKey(refId)) {
            Variable var = Variables.get(refId);
            return var.ObjectId;
        }
        return OptionalLong.empty();
    }

    public void setMember(OptionalLong objId, Long memberIndex, OptionalLong newMember) {
        if (objId.isPresent() && Objects.containsKey(objId.getAsLong())) {
            LivenessInfo object = Objects.get(objId.getAsLong());
            if (object.Members.length > memberIndex) {
                OptionalLong oldMember = object.Members[memberIndex.intValue()].Id;
                if (oldMember.isPresent()) {
                    killObj(oldMember.getAsLong());
                }
                object.Members[memberIndex.intValue()].Id = newMember;
            } 
        }
    }

    public void setReturnInfo(OptionalLong objId, Long argId, Optional<String> structName, String ownership) {
        int membersLen = 0;
        StructMember[] members = null;

        if (objId.isPresent()) {
            members = Objects.get(objId.getAsLong()).Members;
            membersLen = members.length;
        }

        OptionalLong[] membersAsArgs = new OptionalLong[membersLen];

        for (int i=0; i<membersLen; i++) {
            OptionalLong memberObj = members[i].Id;
            membersAsArgs[i] = OptionalLong.empty();

            for (int j=-1; j>argId; j--) {
                OptionalLong argObj = getObject(Long.valueOf(j));
                if (memberObj.isPresent() && argObj.isPresent() 
                 && memberObj.getAsLong() == argObj.getAsLong()) {
                    membersAsArgs[i] = OptionalLong.of((-1 * j) - 1);
                }
            }
        }

        Returns = new ReturnInfo(structName, ownership, membersAsArgs);
    }

    public void printMaps() {
        System.out.println("Function: " + FunctionName);
        this.printObjects();
        this.printVariables();
    }

    public void printObjects() {
        System.out.println("Objects: ");
        for (HashMap.Entry<Long, LivenessInfo> entry : Objects.entrySet()) {
            System.out.print("\t" + entry.getKey() + ": " + Boolean.toString(entry.getValue().Liveness));
            if (entry.getValue().Members != null && entry.getValue().Members.length > 0) {
                System.out.print(", Members: [");
                for (StructMember mem : entry.getValue().Members) {
                    if (mem.Id.isPresent()) {
                        System.out.print(mem.Id.getAsLong());
                        System.out.print(" ");
                        System.out.print(mem.Ownership);
                    } else {
                        System.out.print("const");
                    }
                    System.out.print(", ");
                }
                System.out.println("]");
            }
        }
        System.out.println();
    }

    public void printVariables() {
        System.out.println("Variables: ");
        for (HashMap.Entry<Long, Variable> entry : Variables.entrySet()) {
            System.out.println("\t" + entry.getKey() + ": " + entry.getValue().ObjectId);
        }
        System.out.println();
    }
}