package com.vaultexam;

import com.vaultexam.Dtos.AskResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatService {

    private static final Pattern CITATION = Pattern.compile("\\[Pages?\\s*([^\\]]+)]", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private static final String SYSTEM = """
            You are a strict document assistant for a confidential, offline system.
            Answer ONLY using the CONTEXT provided. Never use outside knowledge.
            After EVERY claim, cite its source page in the form [Page N].
            If the context does not contain the answer, reply exactly:
            "The document does not contain this information."
            """;

    private final ChatClient chat;
    private final Retriever retriever;

    public ChatService(ChatClient chat, Retriever retriever) {
        this.chat = chat;
        this.retriever = retriever;
    }

    public AskResponse ask(String documentId, String question) {
        List<Document> docs = retriever.search(documentId, question, 4);

        String answer = chat.prompt()
                .system(SYSTEM)
                .user(u -> u.text("CONTEXT:\n{ctx}\n\nQUESTION: {q}")
                        .param("ctx", Retriever.format(docs))
                        .param("q", question))
                .call()
                .content();

        Set<Integer> retrieved = new TreeSet<>();
        docs.forEach(d -> retrieved.add(Retriever.page(d)));

        Set<Integer> cited = new TreeSet<>();
        Matcher m = CITATION.matcher(answer == null ? "" : answer);
        while (m.find()) {
            Matcher n = NUMBER.matcher(m.group(1));
            while (n.find()) {
                int p = Integer.parseInt(n.group());
                if (retrieved.contains(p)) cited.add(p);   // drop hallucinated page numbers
            }
        }
        return new AskResponse(answer, new ArrayList<>(cited.isEmpty() ? retrieved : cited));
    }
}
