package com.legal.documentprocessing.composite;

/**
 * Composite Pattern: Leaf component representing a paragraph
 * Cannot contain other components
 */
public class ParagraphComponent extends AbstractDocumentComponent {

    private String text;

    public ParagraphComponent(String name, String text) {
        super(name, ComponentType.PARAGRAPH);
        this.text = text;
    }

    @Override
    protected void renderInternal(StringBuilder sb, int indent) {
        sb.append(getIndent(indent))
          .append("Paragraph: ").append(name).append("\n");
        sb.append(getIndent(indent + 1))
          .append(text.length() > 100 ? text.substring(0, 100) + "..." : text)
          .append("\n");
    }

    public String getText() {
        return text;
    }

    @Override
    public void add(DocumentComponent component) {
        throw new UnsupportedOperationException("Paragraph cannot contain child components");
    }

    @Override
    public void remove(DocumentComponent component) {
        throw new UnsupportedOperationException("Paragraph cannot contain child components");
    }
}
