package vlee12.parser;

import java.util.List;

public class Fun {
    public final String name;
    public final List<String> formals;
    public final Statement body;

    Fun(String name, List<String> formals, Statement body) {
        this.name = name;
        this.formals = formals;
        this.body = body;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("(defn ");
        builder.append(name).append(" [");
        for (int i = 0; i < formals.size(); i++) {
            builder.append(formals.get(i));
            if (i != formals.size() - 1)
                builder.append(" ");
        }
        builder.append("] ").append(body).append(")");
        return builder.toString();
    }
}
