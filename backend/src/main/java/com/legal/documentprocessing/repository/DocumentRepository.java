package com.legal.documentprocessing.repository;

import com.legal.documentprocessing.model.Document;
import com.legal.documentprocessing.model.DocumentSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, String> {
    List<DocumentSummary> findAllSummariesBy();
    List<DocumentSummary> findSummaryByStatus(Document.DocumentStatus status);
    List<DocumentSummary> findSummaryByCategory(Document.DocumentCategory category);
}
