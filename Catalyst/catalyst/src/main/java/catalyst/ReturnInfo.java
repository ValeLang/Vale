package catalyst;

import java.util.*;

public class ReturnInfo {
    public Optional<String> StructName;
    public String Ownership;
    public OptionalLong[] MemberArgIdxs; 

    public ReturnInfo(Optional<String> sn, String own, OptionalLong[] mai) {
        this.StructName = sn;
        this.Ownership = own;
        this.MemberArgIdxs = mai;
    }
}