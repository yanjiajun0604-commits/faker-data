-- Run only in an authorized empty test schema. No user/account changes.
CREATE TABLE demo_virtual_documents (
    id VARCHAR(36) PRIMARY KEY
) ENGINE=InnoDB;
CREATE TABLE demo_virtual_contents (
    id VARCHAR(64) PRIMARY KEY
) ENGINE=InnoDB;
CREATE TABLE demo_virtual_tasks (
    id VARCHAR(36) PRIMARY KEY,
    document_id VARCHAR(36) NOT NULL,
    input_id VARCHAR(64) NOT NULL,
    output_id VARCHAR(64) NOT NULL,
    FOREIGN KEY (document_id) REFERENCES demo_virtual_documents(id),
    FOREIGN KEY (input_id) REFERENCES demo_virtual_contents(id),
    FOREIGN KEY (output_id) REFERENCES demo_virtual_contents(id)
) ENGINE=InnoDB;
