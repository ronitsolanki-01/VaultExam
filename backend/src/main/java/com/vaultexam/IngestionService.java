package com.vaultexam;

import com.vaultexam.Dtos.UploadResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IngestionService {

    private final VectorStore vectorStore;
    // default splitter (~800 tokens per chunk); retrieval counts are tuned to fit num-ctx
    private final TokenTextSplitter splitter = new TokenTextSplitter();

    public IngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public UploadResponse ingest(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename() == null ? "document.pdf" : file.getOriginalFilename();
        if (!fileName.toLowerCase().endsWith(".pdf")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF files are supported");
        }

        String documentId = UUID.randomUUID().toString();
        Path tmp = Files.createTempFile("vaultexam-", ".pdf");
        try {
            file.transferTo(tmp);

            // default config = one Document per page, with "page_number" metadata
            List<Document> pages = new PagePdfDocumentReader(new FileSystemResource(tmp)).get();
            int totalPages = pages.size();

            List<Document> tagged = new ArrayList<>();
            for (Document page : pages) {
                String text = page.getText();
                if (text == null || text.isBlank()) continue;
                Map<String, Object> meta = new HashMap<>(page.getMetadata());
                meta.put("document_id", documentId);
                meta.put("file_name", fileName);
                tagged.add(Document.builder().text(text).metadata(meta).build());
            }
            if (tagged.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No extractable text found (scanned PDF? run OCR first)");
            }

            List<Document> chunks = splitter.apply(tagged);   // metadata is carried to each chunk
            vectorStore.add(chunks);
            return new UploadResponse(documentId, fileName, totalPages, chunks.size());
        } finally {
            Files.deleteIfExists(tmp);   // confidential file never stays on disk
        }
    }

    public void delete(String documentId) {
        vectorStore.delete(new FilterExpressionBuilder().eq("document_id", documentId).build());
    }
}
