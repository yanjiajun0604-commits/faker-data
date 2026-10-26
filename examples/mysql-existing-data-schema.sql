-- Only in a separately authorized, isolated test database with unused table names.
-- Fictional reference data. No existing tables are cleared or altered.
CREATE TABLE demo_lookup_user (
  id VARCHAR(36) PRIMARY KEY,
  display_name VARCHAR(40) NOT NULL,
  org_id VARCHAR(36) NOT NULL,
  enabled INT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO demo_lookup_user VALUES
  ('demo-u1','Alice','demo-org1',1),
  ('demo-u2','Bob','demo-org2',1),
  ('demo-u3','Disabled example','demo-org3',0);

CREATE TABLE demo_lookup_document (
  id VARCHAR(36) PRIMARY KEY,
  sequence_no BIGINT NOT NULL,
  state VARCHAR(16) NOT NULL,
  user_id VARCHAR(36) NOT NULL,
  user_name VARCHAR(40) NOT NULL,
  org_id VARCHAR(36) NOT NULL,
  CONSTRAINT fk_lookup_user FOREIGN KEY(user_id) REFERENCES demo_lookup_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Ordinary child rows, not workflow-engine tasks.
CREATE TABLE demo_lookup_item (
  id VARCHAR(36) PRIMARY KEY,
  document_id VARCHAR(36) NOT NULL,
  user_id VARCHAR(36) NOT NULL,
  CONSTRAINT fk_lookup_document FOREIGN KEY(document_id) REFERENCES demo_lookup_document(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
