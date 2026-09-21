package com.credisynch.api.persistence;

/** pgvector's text literal format ("[0.1,0.2,...]"), shared by every repository that writes a vector column. */
public final class VectorFormat {

    private VectorFormat() {}

    public static String literal(float[] embedding) {
        StringBuilder literal = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                literal.append(',');
            }
            literal.append(embedding[i]);
        }
        return literal.append(']').toString();
    }
}
