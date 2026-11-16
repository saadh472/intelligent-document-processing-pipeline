CREATE TABLE documents (
    id VARCHAR(255) PRIMARY KEY,
    file_name VARCHAR(255),
    file_type VARCHAR(255),
    mime_type VARCHAR(255),
    file_size BIGINT,
    content BYTEA,
    content_text TEXT,
    status VARCHAR(255),
    category VARCHAR(255),
    uploaded_at TIMESTAMP,
    processed_at TIMESTAMP,
    error_message VARCHAR(255)
);
CREATE INDEX documents_uploaded_at_idx ON documents(uploaded_at);
CREATE INDEX documents_status_idx ON documents(status);
CREATE INDEX documents_category_idx ON documents(category);
