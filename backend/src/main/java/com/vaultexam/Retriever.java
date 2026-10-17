package com.vaultexam;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

@Component
public class Retriever {

    private final VectorStore vectorStore;

    public Retriever(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /** Similarity search restricted to one document via metadata filter. */
    public List<Document> search(String documentId, String query, int topK) {
        List<Document> docs = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression(new FilterExpressionBuilder().eq("document_id", documentId).build())
                .build());
        if (docs == null || docs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No content found for documentId " + documentId);
        }
        return docs;
    }

    /** Wide pool, then a seeded random sample => different exam sets see different material. */
    public List<Document> sample(String documentId, String query, int pool, int take, long seed) {
        List<Document> docs = new ArrayList<>(search(documentId, query, pool));
        Collections.shuffle(docs, new Random(seed));
        return docs.stream().limit(take).sorted(Comparator.comparingInt(Retriever::page)).toList();
    }

    public static int page(Document d) {
        Object p = d.getMetadata().get("page_number");
        return p instanceof Number n ? n.intValue() : 0;
    }

    public static String format(List<Document> docs) {
        StringBuilder sb = new StringBuilder();
        for (Document d : docs) {
            sb.append("[Page ").append(page(d)).append("]\n").append(d.getText()).append("\n\n");
        }
        return sb.toString();
    }
}
