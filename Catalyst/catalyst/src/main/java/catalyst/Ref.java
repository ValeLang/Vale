package catalyst;

import java.util.Optional;

public class Ref {
    public String Name;
    public String Variability;
    public String Ownership;
    // Empty if Ref does not point to struct
    public Optional<String> StructName;

    public Ref(String name, String var, String own, Optional<String> sn) {
        this.Name = name;
        this.Variability = var;
        this.Ownership = own;
        this.StructName = sn;
    }
}