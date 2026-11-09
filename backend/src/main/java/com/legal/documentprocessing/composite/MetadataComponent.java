package com.legal.documentprocessing.composite;

import java.util.HashMap;
import java.util.Map;

/**
 * Composite Pattern: Leaf component representing metadata
 * Stores key-value pairs of document metadata
 */
public class MetadataComponent extends AbstractDocumentComponent {

    private Map<String, String> metadata;

    public MetadataComponent(String name) {
        super(name, ComponentType.METADATA);
        this.metadata = new HashMap<>();
    }

    public void addMetadata(String key, String value) {
        metadata.put(key, value);
    }

    public String getMetadata(String key) {
        return metadata.get(key);
    }

    public Map<String, String> getAllMetadata() {
        return new HashMap<>(metadata);
    }

    @Override
    protected void renderInternal(StringBuilder sb, int indent) {
        sb.append(getIndent(indent))
          .append("Metadata: ").append(name).append("\n");

        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            sb.append(getIndent(indent + 1))
              .append(entry.getKey()).append(": ").append(entry.getValue())
              .append("\n");
        }
    }

    @Override
    public void add(DocumentComponent component) {
        throw new UnsupportedOperationException("Metadata cannot contain child components");
    }

    @Override
    public void remove(DocumentComponent component) {
        throw new UnsupportedOperationException("Metadata cannot contain child components");
    }
}
