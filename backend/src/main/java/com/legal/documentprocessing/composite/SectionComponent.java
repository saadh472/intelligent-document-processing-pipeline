package com.legal.documentprocessing.composite;

/**
 * Composite Pattern: Composite component representing a section
 * Can contain paragraphs, subsections, etc.
 */
public class SectionComponent extends AbstractDocumentComponent {

    private int sectionNumber;
    private String sectionTitle;

    public SectionComponent(String name, int sectionNumber, String sectionTitle) {
        super(name, ComponentType.SECTION);
        this.sectionNumber = sectionNumber;
        this.sectionTitle = sectionTitle;
    }

    @Override
    protected void renderInternal(StringBuilder sb, int indent) {
        sb.append(getIndent(indent))
          .append("Section ").append(sectionNumber)
          .append(": ").append(sectionTitle).append("\n");

        for (DocumentComponent child : getChildren()) {
            if (child instanceof AbstractDocumentComponent) {
                ((AbstractDocumentComponent) child).renderInternal(sb, indent + 1);
            } else {
                sb.append(getIndent(indent + 1))
                  .append(child.render()).append("\n");
            }
        }
    }

    public int getSectionNumber() {
        return sectionNumber;
    }

    public String getSectionTitle() {
        return sectionTitle;
    }
}
