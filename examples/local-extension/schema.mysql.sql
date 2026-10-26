-- Run manually in a dedicated test database before the example extension.
CREATE TABLE demo_extension_document (
    id VARCHAR(36) PRIMARY KEY,
    state VARCHAR(20) NOT NULL
) ENGINE=InnoDB;
CREATE TABLE demo_extension_payload (
    id VARCHAR(36) PRIMARY KEY,
    document_id VARCHAR(36) NOT NULL,
    payload BLOB NOT NULL,
    FOREIGN KEY (document_id) REFERENCES demo_extension_document(id)
) ENGINE=InnoDB;
