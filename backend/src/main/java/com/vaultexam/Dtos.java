package com.vaultexam;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class Dtos {
    private Dtos() {}

    // ---------- Upload ----------
    public record UploadResponse(String documentId, String fileName, int totalPages, int chunksStored) {}

    // ---------- Chat ----------
    public record AskRequest(@NotBlank String documentId, @NotBlank String question) {}
    public record AskResponse(String answer, List<Integer> sourcePages) {}

    // ---------- Exam ----------
    public enum Difficulty { RECALL, APPLICATION, ANALYTICAL_EDGE_CASES }

    public record ExamRequest(
            @NotBlank String documentId,
            @NotBlank String instituteName,
            @NotBlank String examType,
            @NotNull Difficulty difficulty,
            @Min(0) @Max(30) int mcqCount,
            @Min(0) @Max(20) int shortAnswerCount,
            @Min(0) @Max(10) int longAnswerCount,
            @Min(1) @Max(3) Integer sets) {}   // optional: 1..3 => Set A/B/C

    // LLM structured-output targets (also returned to the client).
    // Integer (not int) so a model returning null doesn't crash parsing.
    public record Mcq(String question, List<String> options, Integer correctIndex, String explanation, Integer sourcePage) {}
    public record ShortQ(String question, String modelAnswer, Integer sourcePage) {}
    public record LongQ(String question, String modelAnswer, List<String> keyPoints, Integer sourcePage) {}

    public record ExamPaper(String setLabel, List<Mcq> mcqs, List<ShortQ> shortAnswers, List<LongQ> longAnswers) {}

    public record ExamResponse(String examId, String instituteName, String examType, Difficulty difficulty,
                               int totalMarks, int durationMinutes,
                               int mcqMarks, int shortMarks, int longMarks,
                               List<ExamPaper> papers) {}
}