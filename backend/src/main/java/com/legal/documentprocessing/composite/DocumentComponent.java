package com.legal.documentprocessing.composite;

import java.util.List;

/**
 * Composite Pattern: Interface for document structure components
 * Represents hierarchical document structure (documents, sections, paragraphs, metadata)
 */
public interface DocumentComponent {
    /**
     * Renders the component as a string representation
     */
    String render();

    /**
     * Adds a child component
     */
    void add(DocumentComponent component);

    /**
     * Removes a child component
     */
    void remove(DocumentComponent component);

    /**
     * Gets all child components
     */
    List<DocumentComponent> getChildren();

    /**
     * Gets the component name/title
     */
    String getName();

    /**
     * Gets the component type
     */
    ComponentType getType();

    enum ComponentType {
        DOCUMENT, SECTION, PARAGRAPH, METADATA, TABLE, IMAGE
    }
}
