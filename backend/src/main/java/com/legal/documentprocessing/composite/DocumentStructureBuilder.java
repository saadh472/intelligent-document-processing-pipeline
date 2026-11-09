package com.legal.documentprocessing.composite;

import com.legal.documentprocessing.model.Document;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Builder for creating document structure using Composite Pattern
 * Parses document content and builds hierarchical structure
 */
@Component
public class DocumentStructureBuilder {

    private static final Pattern SECTION_PATTERN = Pattern.compile(
        "^(\\d+[.])?\\s*([A-Z][A-Za-z\\s]+)$", Pattern.MULTILINE
    );
    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile(
        "\\n\\s*([A-Z][^\\n]{20,})\\s*\\n"
    );

    /**
     * Builds document structure from a Document entity
     */
    public DocumentComposite buildStructure(Document document) {
        DocumentComposite docComposite = new DocumentComposite(
            document.getFileName(),
            document.getId(),
            document.getFileType()
        );

        // Add metadata
        MetadataComponent metadata = new MetadataComponent("Document Metadata");
        metadata.addMetadata("File Type", document.getFileType());
        metadata.addMetadata("File Size", String.valueOf(document.getFileSize()));
        metadata.addMetadata("MIME Type", document.getMimeType());
        if (document.getUploadedAt() != null) {
            metadata.addMetadata("Uploaded At", document.getUploadedAt().toString());
        }
        docComposite.add(metadata);

        // Parse content into sections and paragraphs
        if (document.getContentText() != null && !document.getContentText().isEmpty()) {
            parseContent(docComposite, document.getContentText());
        }

        return docComposite;
    }

    private void parseContent(DocumentComposite document, String content) {
        String[] lines = content.split("\\n");
        SectionComponent currentSection = null;
        StringBuilder currentParagraph = new StringBuilder();
        int sectionNumber = 1;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                if (currentParagraph.length() > 0) {
                    addParagraph(currentSection, document, currentParagraph.toString());
                    currentParagraph = new StringBuilder();
                }
                continue;
            }

            // Check if line is a section header
            if (isSectionHeader(line)) {
                if (currentSection != null && currentParagraph.length() > 0) {
                    addParagraph(currentSection, document, currentParagraph.toString());
                    currentParagraph = new StringBuilder();
                }
                currentSection = new SectionComponent(
                    "Section " + sectionNumber,
                    sectionNumber++,
                    line
                );
                document.add(currentSection);
            } else {
                if (currentParagraph.length() > 0) {
                    currentParagraph.append(" ");
                }
                currentParagraph.append(line);
            }
        }

        // Add remaining paragraph
        if (currentParagraph.length() > 0) {
            addParagraph(currentSection, document, currentParagraph.toString());
        }
    }

    private boolean isSectionHeader(String line) {
        // Use SECTION_PATTERN to identify section headers
        // Also check for Roman numerals and short all-caps lines
        return line.length() < 100 &&
               (SECTION_PATTERN.matcher(line).matches() ||
                line.matches("^[IVX]+[.].*") ||
                line.matches("^[A-Z][A-Z\\s]+$"));
    }

    private void addParagraph(SectionComponent section, DocumentComposite document, String text) {
        if (text.length() < 10) return; // Skip very short paragraphs

        // Validate paragraph using PARAGRAPH_PATTERN (check if it starts with capital and is substantial)
        String testText = "\n" + text + "\n";
        if (!PARAGRAPH_PATTERN.matcher(testText).find() && text.length() < 20) {
            return; // Skip if doesn't match paragraph pattern and is too short
        }

        ParagraphComponent paragraph = new ParagraphComponent(
            "Paragraph " + System.currentTimeMillis(),
            text
        );

        if (section != null) {
            section.add(paragraph);
        } else {
            document.add(paragraph);
        }
    }
}
