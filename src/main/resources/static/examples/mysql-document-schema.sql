-- Run only in an authorized, isolated test database with unused table names.
-- This script creates example tables. It does not delete or alter existing data.
-- State strings are illustrative; these tables are not a workflow engine.
CREATE TABLE demo_document (
  id VARCHAR(36) PRIMARY KEY,
  sequence_no BIGINT NOT NULL,
  document_no VARCHAR(64) NOT NULL,
  batch_tag VARCHAR(64) NOT NULL,
  state VARCHAR(16) NOT NULL,
  total_amount DECIMAL(12,2) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE demo_document_extension (
  id VARCHAR(36) PRIMARY KEY,
  category VARCHAR(32) NOT NULL,
  state_copy VARCHAR(16) NOT NULL,
  CONSTRAINT fk_demo_document_extension FOREIGN KEY(id) REFERENCES demo_document(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE demo_document_line (
  id VARCHAR(36) PRIMARY KEY,
  document_id VARCHAR(36) NOT NULL,
  document_state VARCHAR(16) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  CONSTRAINT fk_demo_document_line FOREIGN KEY(document_id) REFERENCES demo_document(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
