package catalyst;

import java.util.OptionalLong;

public class StructMember {
    OptionalLong Id;
    String Ownership;

    public StructMember(OptionalLong id, String ownership) {
        this.Id = id;
        this.Ownership = ownership;
    }
}