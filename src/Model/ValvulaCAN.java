package Model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ValvulaCAN(long id, String nome, Map<String, String> identificadores) {
    public ValvulaCAN {
        identificadores = Collections.unmodifiableMap(new LinkedHashMap<>(identificadores));
    }

    @Override public String toString() { return nome; }
}
