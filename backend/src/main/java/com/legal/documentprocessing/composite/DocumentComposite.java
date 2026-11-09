package com.legal.documentprocessing.composite;

/**
 * Composite Pattern: Composite component representing a document
 * Can contain sections, paragraphs, and other components
 */
public class DocumentComposite extends AbstractDocumentComponent {

    private String documentId;
    private String documentType;

    public DocumentComposite(String name, String documentId, String documentType) {
        super(name, ComponentType.DOCUMENT);
        this.documentId = documentId;
        this.documentType = documentType;
    }

    @Override
    protected void renderInternal(StringBuilder sb, int indent) {
        sb.append(getIndent(indent))
          .append("Document: ").append(name)
          .append(" [ID: ").append(documentId)
          .append(", Type: ").append(documentType).append("]\n");

        for (DocumentComponent child : getChildren()) {
            if (child instanceof AbstractDocumentComponent) {
                ((AbstractDocumentComponent) child).renderInternal(sb, indent + 1);
            } else {
                sb.append(getIndent(indent + 1))
                  .append(child.render()).append("\n");
            }
        }
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getDocumentType() {
        return documentType;
    }
}
