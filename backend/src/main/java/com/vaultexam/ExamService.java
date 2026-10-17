package com.vaultexam;

import com.vaultexam.Dtos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

@Service
public class ExamService {

    private static final Logger log = LoggerFactory.getLogger(ExamService.class);

    static final int MCQ_MARKS = 1, SHORT_MARKS = 3, LONG_MARKS = 10;
    private static final int POOL = 16, TAKE = 3, ATTEMPTS = 4;

    private static final String SYSTEM = """
            You are a senior exam paper setter. Create questions ONLY from the CONTEXT excerpts.
            Never invent facts. Every question must be answerable from the context.
            Respond with ONE valid JSON object only. No commentary, no markdown fences.
            Keep every string value on a single line with no line breaks.
            """;

    private static final String COMMON = """

            Difficulty: {level}
            Do NOT repeat or rephrase any of these existing questions: {avoid}

            CONTEXT (each excerpt is labelled with its page number):
            {ctx}
            """;

    private static final String MCQ_TASK = """
            Create ONE multiple-choice question. Return a single JSON object with:
            "question", "options" (array of exactly 4 plausible options, plain text, no "A)" prefixes),
            "correctIndex" (integer 0-3), "explanation" (one short line),
            "sourcePage" (integer taken from a [Page N] label).
            """ + COMMON;

    private static final String SHORT_TASK = """
            Create ONE short-answer question (answer of 2-4 sentences). Return a single JSON object with:
            "question", "modelAnswer", "sourcePage" (integer taken from a [Page N] label).
            """ + COMMON;

    private static final String LONG_TASK = """
            Create ONE long-answer / essay question. Return a single JSON object with:
            "question", "modelAnswer" (detailed, one paragraph), "keyPoints" (array of 3-5 short marking points),
            "sourcePage" (integer taken from a [Page N] label).
            """ + COMMON;

    private final ChatClient chat;
    private final Retriever retriever;

    public ExamService(ChatClient chat, Retriever retriever) {
        this.chat = chat;
        this.retriever = retriever;
    }

    public ExamResponse generate(ExamRequest r) {
        int sets = r.sets() == null ? 1 : r.sets();
        String level = levelText(r.difficulty());
        List<String> used = new ArrayList<>();   // questions so far, across all sets
        List<ExamPaper> papers = new ArrayList<>();

        for (int s = 0; s < sets; s++) {
            String label = String.valueOf((char) ('A' + s));
            long seed = r.documentId().hashCode() * 31L + s * 7919L;

            List<Mcq> mcqs = mcqs(r, level, used, seed);
            List<ShortQ> shorts = shorts(r, level, used, seed + 1);
            List<LongQ> longs = longs(r, level, used, seed + 2);
            papers.add(new ExamPaper(label, mcqs, shorts, longs));
        }

        int total = r.mcqCount() * MCQ_MARKS + r.shortAnswerCount() * SHORT_MARKS + r.longAnswerCount() * LONG_MARKS;
        int minutes = r.mcqCount() + r.shortAnswerCount() * 4 + r.longAnswerCount() * 12;
        return new ExamResponse(UUID.randomUUID().toString(), r.instituteName(), r.examType(), r.difficulty(),
                total, minutes, MCQ_MARKS, SHORT_MARKS, LONG_MARKS, papers);
    }

    private List<Mcq> mcqs(ExamRequest r, String level, List<String> used, long seed) {
        List<Mcq> out = new ArrayList<>();
        for (int i = 0; i < r.mcqCount(); i++) {
            List<Document> docs = retriever.sample(r.documentId(),
                    "key definitions, facts, terms, rules", POOL, TAKE, seed + i * 101L);
            Mcq q = one(Mcq.class, "MCQ", MCQ_TASK, params(level, used, docs),
                    x -> notBlank(x.question()) && x.options() != null && x.options().size() == 4
                             && x.correctIndex() >= 0 && x.correctIndex() < 4);
            out.add(new Mcq(q.question(), q.options(), q.correctIndex(), q.explanation(), fixPage(q.sourcePage(), docs)));
            used.add(q.question());
        }
        return out;
    }

    private List<ShortQ> shorts(ExamRequest r, String level, List<String> used, long seed) {
        List<ShortQ> out = new ArrayList<>();
        for (int i = 0; i < r.shortAnswerCount(); i++) {
            List<Document> docs = retriever.sample(r.documentId(),
                    "explanations, processes, comparisons, causes", POOL, TAKE, seed + i * 101L);
            ShortQ q = one(ShortQ.class, "short-answer", SHORT_TASK, params(level, used, docs),
                    x -> notBlank(x.question()) && notBlank(x.modelAnswer()));
            out.add(new ShortQ(q.question(), q.modelAnswer(), fixPage(q.sourcePage(), docs)));
            used.add(q.question());
        }
        return out;
    }

    private List<LongQ> longs(ExamRequest r, String level, List<String> used, long seed) {
        List<LongQ> out = new ArrayList<>();
        for (int i = 0; i < r.longAnswerCount(); i++) {
            List<Document> docs = retriever.sample(r.documentId(),
                    "concepts, analysis, implications, reasoning", POOL, TAKE, seed + i * 101L);
            LongQ q = one(LongQ.class, "long-answer", LONG_TASK, params(level, used, docs),
                    x -> notBlank(x.question()) && notBlank(x.modelAnswer()));
            List<String> points = q.keyPoints() == null ? List.of() : q.keyPoints();
            out.add(new LongQ(q.question(), q.modelAnswer(), points, fixPage(q.sourcePage(), docs)));
            used.add(q.question());
        }
        return out;
    }

    private Map<String, Object> params(String level, List<String> used, List<Document> docs) {
        return Map.of("level", level,
                "ctx", Retriever.format(docs),
                "avoid", used.isEmpty() ? "none" : String.join(" | ", used));
    }

    /** Small local models sometimes return malformed JSON: validate each question and retry. */
    private <T> T one(Class<T> type, String name, String task, Map<String, Object> params, Predicate<T> valid) {
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                T out = chat.prompt()
                        .system(SYSTEM)
                        .user(u -> u.text(task).params(params))
                        .call()
                        .entity(type);
                if (out != null && valid.test(out)) return out;
                log.warn("Attempt {} produced an invalid {} question", attempt, name);
            } catch (Exception e) {
                log.warn("Attempt {} failed for {}: {}", attempt, name, e.getMessage());
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Local model failed to produce a valid " + name + " question after " + ATTEMPTS + " attempts");
    }

    /** If the model gave no page, or one that wasn't in the excerpts, use the first excerpt's page. */
    private Integer fixPage(Integer page, List<Document> docs) {
        if (page != null && docs.stream().anyMatch(d -> Retriever.page(d) == page)) return page;
        return docs.isEmpty() ? null : Retriever.page(docs.get(0));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String levelText(Difficulty d) {
        return switch (d) {
            case RECALL -> "RECALL - direct facts, definitions and terminology straight from the text.";
            case APPLICATION -> "APPLICATION - apply concepts to new scenarios and short problems.";
            case ANALYTICAL_EDGE_CASES ->
                    "ANALYTICAL_EDGE_CASES - multi-step reasoning, exceptions, edge cases, 'what if' and trap options.";
        };
    }
}