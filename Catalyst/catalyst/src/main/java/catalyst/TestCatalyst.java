package catalyst;

import java.io.*;
import java.util.*;
import java.util.function.Function;

import org.json.simple.*;
import org.json.simple.parser.*;


public class TestCatalyst {
    private static final String MISSING_NODES = "Did not find all expected AST nodes in revised .vast file";
    private static final String MISSING_KL = "Expected knownLive to be true, but knownLive was false";

    private static ArrayList<JSONObject> recursiveCollect(JSONObject astNode, Function<JSONObject, Boolean> checkFields) {
        ArrayList<JSONObject> returnList = new ArrayList<JSONObject>();
        if (checkFields.apply(astNode)) {
            returnList.add(astNode);
        }

        // iterate fields
        for (Iterator iterator = astNode.keySet().iterator(); iterator.hasNext(); ) {
            String field = (String) iterator.next();
            Object value = astNode.get(field);
            if (value instanceof JSONObject) {
                returnList.addAll(recursiveCollect((JSONObject)value, checkFields));
            } else if (value instanceof JSONArray) {
                for (Object arrayVal : (JSONArray)value) {
                    assert !(arrayVal instanceof JSONArray) : "Unexpected type of JSONArray entry (JSONArray)";
                    if (arrayVal instanceof JSONObject) {
                        returnList.addAll(recursiveCollect((JSONObject)arrayVal, checkFields));
                    }
                }
            } 
        }
        
        return returnList;
    }

    private static void test1to3(String outFile) {
        JSONParser parser = new JSONParser();
        try {
            JSONObject ast = (JSONObject)parser.parse(new FileReader(outFile));
            ArrayList<JSONObject> memberLoads = recursiveCollect(ast, (node) -> { 
                return ((String)node.get("__type")).equals("MemberLoad") && ((String)node.get("memberName")).equals("fuel__ut_0"); });
            
            assert memberLoads.size() == 1 : MISSING_NODES;
            assert (boolean)memberLoads.get(0).get("structKnownLive") : MISSING_KL;
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static void runTests() {
        //TODO: get paths in a portable manner
        String[] args1 = {
            "C:\\Users\\theow\\Desktop\\Thesis\\Catalyst\\testing\\test1\\out\\build.vast", 
            "C:\\Users\\theow\\Desktop\\Thesis\\Catalyst\\testing\\test1\\out\\speedy-build.vast" };
        String[] args2 = {
            "C:\\Users\\theow\\Desktop\\Thesis\\Catalyst\\testing\\test2\\out\\build.vast", 
            "C:\\Users\\theow\\Desktop\\Thesis\\Catalyst\\testing\\test2\\out\\speedy-build.vast" };
        String[] args3 = {
            "C:\\Users\\theow\\Desktop\\Thesis\\Catalyst\\testing\\test3\\out\\build.vast", 
            "C:\\Users\\theow\\Desktop\\Thesis\\Catalyst\\testing\\test3\\out\\speedy-build.vast" };

        Catalyst.main(args1);
        test1to3(args1[1]);
        Catalyst.main(args2);
        test1to3(args2[1]);
        Catalyst.main(args3);
        test1to3(args3[1]);

        System.out.println("Tests completed successfully!");
    }
    
}


