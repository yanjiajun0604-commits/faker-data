-- 在你授权的独立 MySQL 测试库中手动执行。项目 API 不执行建库建表 SQL。
-- 使用新的空表；不会删除、清空或修改已有表结构。
CREATE TABLE ai_demo_parent (
  id VARCHAR(36) PRIMARY KEY,
  name VARCHAR(64) NOT NULL,
  surname VARCHAR(8),
  kind VARCHAR(16) NOT NULL,
  discount INT NOT NULL,
  created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE ai_demo_child (
  id VARCHAR(36) PRIMARY KEY,
  parent_id VARCHAR(36) NOT NULL,
  sequence_no BIGINT NOT NULL,
  amount DECIMAL(12,2),
  phone VARCHAR(32),
  CONSTRAINT fk_ai_demo_parent FOREIGN KEY(parent_id) REFERENCES ai_demo_parent(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE ai_demo_tree (
  id VARCHAR(36) PRIMARY KEY,
  parent_id VARCHAR(36),
  tree_code VARCHAR(32) UNIQUE,
  label VARCHAR(64),
  CONSTRAINT fk_ai_demo_tree FOREIGN KEY(parent_id) REFERENCES ai_demo_tree(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
