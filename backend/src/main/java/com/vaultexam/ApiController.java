package com.vaultexam;

import com.vaultexam.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final IngestionService ingestion;
    private final ChatService chat;
    private final ExamService exams;

    public ApiController(IngestionService ingestion, ChatService chat, ExamService exams) {
        this.ingestion = ingestion;
        this.chat = chat;
        this.exams = exams;
    }

    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResponse upload(@RequestParam("file") MultipartFile file) throws IOException {
        return ingestion.ingest(file);
    }

    @DeleteMapping("/documents/{id}")
    public void delete(@PathVariable String id) {
        ingestion.delete(id);
    }

    @PostMapping("/chat/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest req) {
        return chat.ask(req.documentId(), req.question());
    }

    @PostMapping("/exams/generate")
    public ExamResponse generate(@Valid @RequestBody ExamRequest req) {
        return exams.generate(req);
    }
}
