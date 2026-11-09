package com.legal.documentprocessing.repository;

import com.legal.documentprocessing.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, String> {
    List<Document> findByStatus(Document.DocumentStatus status);
    List<Document> findByCategory(Document.DocumentCategory category);
}
