package com.legal.documentprocessing.pipeline;

import com.legal.documentprocessing.model.Document;

/**
 * Pipes and Filters Architecture: Base interface for filters
 * Each filter processes documents and passes them to the next stage
 */
public interface PipelineFilter {
    /**
     * Processes a document and returns the processed document
     * @param document Input document
     * @return Processed document (may be modified)
     */
    Document process(Document document);

    /**
     * Gets the filter name
     */
    String getFilterName();

    /**
     * Gets the next filter in the pipeline
     */
    PipelineFilter getNext();

    /**
     * Sets the next filter in the pipeline
     */
    void setNext(PipelineFilter next);
}
