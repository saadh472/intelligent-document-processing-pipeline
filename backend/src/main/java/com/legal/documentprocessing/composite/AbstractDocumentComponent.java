package com.legal.documentprocessing.composite;

import java.util.ArrayList;
import java.util.List;

/**
 * Composite Pattern: Abstract base component
 * Provides default implementation for composite operations
 */
public abstract class AbstractDocumentComponent implements DocumentComponent {

    protected String name;
    protected ComponentType type;
    protected List<DocumentComponent> children;

    public AbstractDocumentComponent(String name, ComponentType type) {
        this.name = name;
        this.type = type;
        this.children = new ArrayList<>();
    }

    @Override
    public void add(DocumentComponent component) {
        children.add(component);
    }

    @Override
    public void remove(DocumentComponent component) {
        children.remove(component);
    }

    @Override
    public List<DocumentComponent> getChildren() {
        return new ArrayList<>(children);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ComponentType getType() {
        return type;
    }

    @Override
    public String render() {
        StringBuilder sb = new StringBuilder();
        renderInternal(sb, 0);
        return sb.toString();
    }

    protected abstract void renderInternal(StringBuilder sb, int indent);

    protected String getIndent(int level) {
        return "  ".repeat(level);
    }
}
